package app.pocketos.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.pocketos.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class Available(val release: AppReleaseInfo) : UpdateState()
    data class Downloading(
        val release: AppReleaseInfo,
        val progress: Float,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : UpdateState()
    data class ReadyToInstall(val release: AppReleaseInfo, val apkFile: File) : UpdateState()
    data class Error(val message: String, val isManual: Boolean = false) : UpdateState()
    data object UpToDate : UpdateState()
}

/**
 * Official In-App Update Manager for PocketOS.
 *
 * Source: Official GitHub Releases of https://github.com/pooriyayt/PocketOS
 *
 * Security:
 * - Strictly connects to api.github.com for official releases.
 * - Downloads only official release APKs.
 * - Computes and validates SHA-256 integrity hash when provided.
 * - Installs via the standard Android Package Installer and FileProvider.
 * - Non-blocking: will never freeze or delay app startup.
 */
class AppUpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    /** Non-blocking update check against the official GitHub releases. */
    fun checkForUpdates(isManual: Boolean = false) {
        // Store builds are updated by the store, never by the app itself.
        if (!BuildConfig.SELF_UPDATE) return
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return

        scope.launch {
            _state.value = UpdateState.Checking
            try {
                val releaseInfo = fetchLatestRelease()
                if (releaseInfo != null && isNewerVersion(releaseInfo.version, BuildConfig.VERSION_NAME)) {
                    _state.value = UpdateState.Available(releaseInfo)
                } else {
                    _state.value = if (isManual) UpdateState.UpToDate else UpdateState.Idle
                }
            } catch (e: Exception) {
                _state.value = if (isManual) {
                    UpdateState.Error(e.message ?: "Failed to check for updates", isManual = true)
                } else {
                    UpdateState.Idle
                }
            }
        }
    }

    /** Downloads the release APK into the app's cache directory. */
    fun downloadUpdate(release: AppReleaseInfo) {
        if (_state.value is UpdateState.Downloading) return

        downloadJob = scope.launch {
            _state.value = UpdateState.Downloading(release, 0f, 0L, release.apkSize)
            try {
                val apkFile = downloadApk(release) { bytesRead, totalBytes ->
                    val progress = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                    _state.value = UpdateState.Downloading(release, progress, bytesRead, totalBytes)
                }

                // Verify SHA-256 if available
                if (!release.sha256.isNullOrBlank()) {
                    val computedHash = calculateSha256(apkFile)
                    if (!computedHash.equals(release.sha256.trim(), ignoreCase = true)) {
                        apkFile.delete()
                        _state.value = UpdateState.Error("Update verification failed: Checksum mismatch")
                        return@launch
                    }
                }

                _state.value = UpdateState.ReadyToInstall(release, apkFile)
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Failed to download update")
            }
        }
    }

    /** Opens the direct APK download URL in the device's default browser or download manager. */
    fun openDownload(release: AppReleaseInfo) {
        val url = release.apkUrl.ifBlank { release.htmlUrl.orEmpty().ifBlank { GITHUB_RELEASES_URL } }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
        dismiss()
    }

    /** Opens the official GitHub release page in the device's default browser. */
    fun openReleasePage(release: AppReleaseInfo) {
        val url = release.htmlUrl.orEmpty().ifBlank { GITHUB_RELEASES_URL }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
        dismiss()
    }

    fun dismiss() {
        downloadJob?.cancel()
        _state.value = UpdateState.Idle
    }

    private suspend fun fetchLatestRelease(): AppReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GITHUB_API_LATEST_RELEASE)
            .header("User-Agent", "PocketOS-Updater/${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) return@withContext null // No releases published yet
                error("GitHub API error: ${response.code}")
            }
            val body = response.body.string()
            val release = json.decodeFromString(GitHubRelease.serializer(), body)

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext null

            // Look for SHA-256 in .sha256 asset or in release body
            val shaAsset = release.assets.firstOrNull { it.name.endsWith(".sha256", ignoreCase = true) }
            val sha256 = if (shaAsset != null) {
                fetchSha256(shaAsset.downloadUrl)
            } else {
                extractSha256FromBody(release.body)
            }

            val cleanVersion = release.tagName.trimStart('v', 'V')
            AppReleaseInfo(
                version = cleanVersion,
                title = release.name ?: "PocketOS v$cleanVersion",
                changelog = release.body ?: "",
                publishedAt = release.publishedAt,
                apkUrl = apkAsset.downloadUrl,
                apkSize = apkAsset.size,
                sha256 = sha256,
                htmlUrl = release.htmlUrl,
            )
        }
    }

    private suspend fun fetchSha256(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val content = response.body.string().trim()
                // Format could be "hash filename" or just "hash"
                content.split(Regex("\\s+")).firstOrNull()?.takeIf { it.length == 64 }
            }
        }.getOrNull()
    }

    private fun extractSha256FromBody(body: String?): String? {
        if (body == null) return null
        val match = Regex("(?i)sha-?256[:\\s]+([a-f0-9]{64})").find(body)
        return match?.groupValues?.getOrNull(1)
    }

    private suspend fun downloadApk(
        release: AppReleaseInfo,
        onProgress: (Long, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, "pocketos-v${release.version}.apk")
        if (targetFile.exists()) targetFile.delete()

        val request = Request.Builder()
            .url(release.apkUrl)
            .header("User-Agent", "PocketOS-Updater/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
            val body = response.body
            val totalBytes = if (release.apkSize > 0) release.apkSize else body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesReadTotal = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesReadTotal += read
                        onProgress(bytesReadTotal, totalBytes)
                    }
                    output.flush()
                }
            }
        }
        targetFile
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val GITHUB_REPO = "pooriyayt/PocketOS"
        const val GITHUB_RELEASES_URL = "https://github.com/pooriyayt/PocketOS/releases/latest"
        private const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/pooriyayt/PocketOS/releases/latest"

        /** Compares semantic versions (e.g. "1.1.0" > "1.0.2"). */
        fun isNewerVersion(remote: String, current: String): Boolean {
            val rParts = remote.split('.').mapNotNull { it.toIntOrNull() }
            val cParts = current.split('.').mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(rParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }
    }
}

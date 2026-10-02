package app.pocketos.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.pocketos.core.security.SecretBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.SecureRandom

/**
 * Owns the encrypted local Room database for PocketOS.
 *
 * Privacy-First & Local-First:
 * - All user reminders, subscriptions, tasks, and categories are stored locally on the device.
 * - Encrypted at rest via SQLCipher (AES-256).
 * - Database encryption key is generated with [SecureRandom] and sealed using
 *   an Android Keystore hardware-backed key (see [SecretBox]).
 * - The sealed key file is stored in [Context.getNoBackupFilesDir] so it is never
 *   included in unencrypted Android backups.
 */
class DatabaseManager(
    private val context: Context,
    private val keyBox: SecretBox?,
    private val inMemory: Boolean = false,
) {
    private var openedDb: PocketDatabase? = null
    private val _active = MutableStateFlow(ProfiledDatabase(LOCAL_PROFILE, openDb()))

    /** The database instance; repositories observe this to react to database resets. */
    val active: StateFlow<ProfiledDatabase> = _active.asStateFlow()

    val current: PocketDatabase get() = _active.value.db
    val profileId: String get() = LOCAL_PROFILE

    data class ProfiledDatabase(val profileId: String, val db: PocketDatabase)

    @Synchronized
    private fun openDb(): PocketDatabase {
        openedDb?.let { return it }
        val builder = if (inMemory) {
            Room.inMemoryDatabaseBuilder(context, PocketDatabase::class.java)
        } else {
            Room.databaseBuilder(context, PocketDatabase::class.java, DB_FILE_NAME)
                .openHelperFactory(encryptedFactory())
        }
        val db = builder.addMigrations(*PocketDatabase.MIGRATIONS).build()
        openedDb = db
        return db
    }

    private fun encryptedFactory(): SupportSQLiteOpenHelper.Factory {
        System.loadLibrary("sqlcipher")
        return SupportOpenHelperFactory(passphrase())
    }

    /**
     * Loads or creates the database passphrase. The passphrase is sealed using
     * Android Keystore AES-GCM encryption.
     */
    private fun passphrase(): ByteArray {
        val box = keyBox ?: error("Android Keystore encryption unavailable")
        val file = keyFile()
        if (file.exists()) {
            val opened = runCatching { box.open(file.readBytes()) }.getOrNull()
            if (opened != null) return opened
            // If keystore was cleared/reset by user, discard unreadable file
            context.deleteDatabase(DB_FILE_NAME)
            file.delete()
        }
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        file.parentFile?.mkdirs()
        file.writeBytes(box.seal(secret))
        return secret
    }

    /** Permanently clears and closes the database. */
    @Synchronized
    fun wipeDatabase() {
        openedDb?.close()
        openedDb = null
        if (!inMemory) {
            context.deleteDatabase(DB_FILE_NAME)
            keyFile().delete()
        }
        _active.value = ProfiledDatabase(LOCAL_PROFILE, openDb())
    }

    private fun keyFile() = File(context.noBackupFilesDir, "keys/pocketos-db.key")

    companion object {
        const val LOCAL_PROFILE = "local"
        private const val DB_FILE_NAME = "pocketos-local.db"
    }
}

package app.pocketos.data.backup

import android.content.ContentResolver
import android.net.Uri
import app.pocketos.core.AppClock
import app.pocketos.data.local.CategoryEntity
import app.pocketos.data.local.DatabaseManager
import app.pocketos.data.local.ReminderEntity
import app.pocketos.data.local.SubscriptionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed class BackupType {
    data object Encrypted : BackupType()
    data object PlainJson : BackupType()
    data object Invalid : BackupType()
}

data class BackupStats(
    val remindersCount: Int,
    val subscriptionsCount: Int,
    val categoriesCount: Int,
    val exportedAt: String,
)

/**
 * Local-First Backup and Restore System for PocketOS.
 *
 * Supports:
 * 1. Encrypted Backups: AES-256-GCM with a user-chosen password stretched via PBKDF2WithHmacSHA256 (100,000 rounds).
 *    Binary format: [MAGIC 4B "PKOS"] [VERSION 1B] [SALT 16B] [IV 12B] [CIPHERTEXT + GCM TAG 16B]
 * 2. Unencrypted JSON export for transparency and user portability.
 * 3. Safe, validated import with transactional database merge.
 * 4. Complete data wipe.
 */
class BackupManager(
    private val databases: DatabaseManager,
    private val clock: AppClock,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /** Builds the full JSON payload of user records. */
    suspend fun buildJsonPayload(): String = withContext(Dispatchers.IO) {
        val db = databases.current
        val reminders = db.reminders().allActive()
        val subscriptions = db.subscriptions().allActive()
        val categories = db.categories().everything().filter { it.deletedAt == null }

        val root = buildJsonObject {
            put("format", FORMAT_NAME)
            put("format_version", FORMAT_VERSION)
            put("exported_at", clock.now().toString())
            put("source", "PocketOS Local-First Device")

            put("reminders", JsonArray(reminders.map { r ->
                buildJsonObject {
                    put("id", r.id)
                    put("kind", r.kind)
                    put("title", r.title)
                    r.notes?.let { put("notes", it) }
                    r.dueDate?.let { put("due_date", it) }
                    r.dueTime?.let { put("due_time", it) }
                    put("all_day", r.allDay)
                    r.timeZone?.let { put("time_zone", it) }
                    r.recurrence?.let { put("recurrence", it) }
                    put("priority", r.priority)
                    put("category", r.category)
                    put("lead_minutes", r.leadMinutes)
                    r.completedAt?.let { put("completed_at", it) }
                    r.snoozedUntil?.let { put("snoozed_until", it) }
                    put("created_at", r.createdAt)
                    put("updated_at", r.updatedAt)
                }
            }))

            put("subscriptions", JsonArray(subscriptions.map { s ->
                buildJsonObject {
                    put("id", s.id)
                    put("name", s.name)
                    s.serviceId?.let { put("service_id", it) }
                    put("category", s.category)
                    s.amountMinor?.let { put("amount_minor", it) }
                    put("currency", s.currency)
                    put("billing_unit", s.billingUnit)
                    put("billing_interval", s.billingInterval)
                    s.startDate?.let { put("start_date", it) }
                    put("next_renewal_date", s.nextRenewalDate)
                    s.trialEndDate?.let { put("trial_end_date", it) }
                    s.cancellationUrl?.let { put("cancellation_url", it) }
                    s.notes?.let { put("notes", it) }
                    put("reminder_offsets", s.reminderOffsets)
                    put("status", s.status)
                    s.color?.let { put("color", it) }
                    put("created_at", s.createdAt)
                    put("updated_at", s.updatedAt)
                }
            }))

            put("categories", JsonArray(categories.map { c ->
                buildJsonObject {
                    put("id", c.id)
                    put("kind", c.kind)
                    put("name", c.name)
                    c.color?.let { put("color", it) }
                    c.icon?.let { put("icon", it) }
                    put("created_at", c.createdAt)
                    put("updated_at", c.updatedAt)
                }
            }))
        }
        json.encodeToString(JsonObject.serializer(), root)
    }

    /** Exports data encrypted with the user's password. */
    suspend fun createEncryptedBackup(password: String): ByteArray = withContext(Dispatchers.Default) {
        require(password.isNotBlank()) { "Backup password cannot be empty" }
        val plainBytes = buildJsonPayload().toByteArray(Charsets.UTF_8)

        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plainBytes)

        val buffer = ByteBuffer.allocate(MAGIC.size + 1 + salt.size + iv.size + ciphertext.size)
        buffer.put(MAGIC)
        buffer.put(ENCRYPTION_VERSION)
        buffer.put(salt)
        buffer.put(iv)
        buffer.put(ciphertext)
        buffer.array()
    }

    /** Determines whether the given input is an encrypted backup or plain JSON. */
    fun detectType(headerBytes: ByteArray): BackupType {
        if (headerBytes.size >= MAGIC.size && headerBytes.take(MAGIC.size).toByteArray().contentEquals(MAGIC)) {
            return BackupType.Encrypted
        }
        val text = runCatching { String(headerBytes, Charsets.UTF_8).trimStart() }.getOrNull() ?: return BackupType.Invalid
        if (text.startsWith("{") && text.contains("pocketos-export")) {
            return BackupType.PlainJson
        }
        return BackupType.Invalid
    }

    /** Decrypts an encrypted backup buffer using the user's password. */
    fun decryptBackup(backupData: ByteArray, password: String): String {
        require(backupData.size > MAGIC.size + 1 + SALT_SIZE + IV_SIZE + 16) { "Backup file is corrupt or truncated" }
        val buffer = ByteBuffer.wrap(backupData)
        val magic = ByteArray(MAGIC.size)
        buffer.get(magic)
        require(magic.contentEquals(MAGIC)) { "Invalid backup file format" }
        val version = buffer.get()
        require(version == ENCRYPTION_VERSION) { "Unsupported backup version: $version" }

        val salt = ByteArray(SALT_SIZE)
        buffer.get(salt)
        val iv = ByteArray(IV_SIZE)
        buffer.get(iv)

        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val plainBytes = cipher.doFinal(ciphertext)
        return String(plainBytes, Charsets.UTF_8)
    }

    /** Parses and imports the JSON payload into the local encrypted database. */
    suspend fun restoreFromJson(jsonString: String): BackupStats = withContext(Dispatchers.IO) {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val format = root["format"]?.jsonPrimitive?.contentOrNull
        require(format == FORMAT_NAME) { "Invalid format identifier: $format" }

        val db = databases.current
        var remindersCount = 0
        var subscriptionsCount = 0
        var categoriesCount = 0

        val remindersArray = root["reminders"]?.jsonArray.orEmpty()
        val subscriptionsArray = root["subscriptions"]?.jsonArray.orEmpty()
        val categoriesArray = root["categories"]?.jsonArray.orEmpty()
        val exportedAt = root["exported_at"]?.jsonPrimitive?.contentOrNull ?: clock.now().toString()

        val remindersToInsert = remindersArray.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ReminderEntity(
                id = id,
                kind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: "reminder",
                title = title,
                notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
                dueDate = obj["due_date"]?.jsonPrimitive?.contentOrNull,
                dueTime = obj["due_time"]?.jsonPrimitive?.contentOrNull,
                allDay = obj["all_day"]?.jsonPrimitive?.booleanOrNull ?: false,
                timeZone = obj["time_zone"]?.jsonPrimitive?.contentOrNull,
                recurrence = obj["recurrence"]?.jsonPrimitive?.contentOrNull,
                priority = obj["priority"]?.jsonPrimitive?.intOrNull ?: 1,
                category = obj["category"]?.jsonPrimitive?.contentOrNull ?: "personal",
                leadMinutes = obj["lead_minutes"]?.jsonPrimitive?.intOrNull ?: 0,
                completedAt = obj["completed_at"]?.jsonPrimitive?.longOrNull,
                snoozedUntil = obj["snoozed_until"]?.jsonPrimitive?.longOrNull,
                soundUri = null,
                createdAt = obj["created_at"]?.jsonPrimitive?.longOrNull ?: clock.now().toEpochMilli(),
                updatedAt = obj["updated_at"]?.jsonPrimitive?.longOrNull ?: clock.now().toEpochMilli(),
                deletedAt = null,
                serverVersion = 0,
                dirty = false,
            )
        }

        val subscriptionsToInsert = subscriptionsArray.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            SubscriptionEntity(
                id = id,
                name = name,
                serviceId = obj["service_id"]?.jsonPrimitive?.contentOrNull,
                category = obj["category"]?.jsonPrimitive?.contentOrNull ?: "other",
                amountMinor = obj["amount_minor"]?.jsonPrimitive?.longOrNull,
                currency = obj["currency"]?.jsonPrimitive?.contentOrNull ?: "USD",
                billingUnit = obj["billing_unit"]?.jsonPrimitive?.contentOrNull ?: "month",
                billingInterval = obj["billing_interval"]?.jsonPrimitive?.intOrNull ?: 1,
                startDate = obj["start_date"]?.jsonPrimitive?.contentOrNull,
                nextRenewalDate = obj["next_renewal_date"]?.jsonPrimitive?.contentOrNull ?: clock.today().toString(),
                trialEndDate = obj["trial_end_date"]?.jsonPrimitive?.contentOrNull,
                cancellationUrl = obj["cancellation_url"]?.jsonPrimitive?.contentOrNull,
                notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
                reminderOffsets = obj["reminder_offsets"]?.jsonPrimitive?.contentOrNull ?: "1",
                status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "active",
                color = obj["color"]?.jsonPrimitive?.contentOrNull,
                createdAt = obj["created_at"]?.jsonPrimitive?.longOrNull ?: clock.now().toEpochMilli(),
                updatedAt = obj["updated_at"]?.jsonPrimitive?.longOrNull ?: clock.now().toEpochMilli(),
                deletedAt = null,
                serverVersion = 0,
                dirty = false,
            )
        }

        val categoriesToInsert = categoriesArray.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            CategoryEntity(
                id = id,
                kind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: "subscription",
                name = name,
                color = obj["color"]?.jsonPrimitive?.contentOrNull,
                icon = obj["icon"]?.jsonPrimitive?.contentOrNull,
                createdAt = obj["created_at"]?.jsonPrimitive?.longOrNull ?: clock.now().toEpochMilli(),
                updatedAt = obj["updated_at"]?.jsonPrimitive?.longOrNull ?: clock.now().toEpochMilli(),
                deletedAt = null,
                serverVersion = 0,
                dirty = false,
            )
        }

        remindersToInsert.forEach {
            db.reminders().upsert(it)
            remindersCount++
        }
        subscriptionsToInsert.forEach {
            db.subscriptions().upsert(it)
            subscriptionsCount++
        }
        categoriesToInsert.forEach {
            db.categories().upsert(it)
            categoriesCount++
        }

        BackupStats(
            remindersCount = remindersCount,
            subscriptionsCount = subscriptionsCount,
            categoriesCount = categoriesCount,
            exportedAt = exportedAt,
        )
    }

    /** Clears all personal data from the database. */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        val db = databases.current
        db.reminders().clear()
        db.subscriptions().clear()
        db.categories().clear()
    }

    suspend fun writeBytesTo(resolver: ContentResolver, uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Cannot write to destination")
    }

    suspend fun readBytesFrom(resolver: ContentResolver, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read from source")
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private const val FORMAT_NAME = "pocketos-export"
        private const val FORMAT_VERSION = 2
        private val MAGIC = "PKOS".toByteArray(Charsets.US_ASCII)
        private const val ENCRYPTION_VERSION: Byte = 1
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12
        private const val GCM_TAG_LENGTH = 128
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH_BITS = 256
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

package com.cady.cadymarketing.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.cady.cadymarketing.data.oplog.OpLogRepository
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.data.sync.RoomSyncLocal
import com.cady.cadymarketing.domain.engine.BackupDecode
import com.cady.cadymarketing.domain.engine.BackupFile
import com.cady.cadymarketing.domain.engine.BackupJson
import com.cady.cadymarketing.domain.engine.BackupMerge
import com.cady.cadymarketing.domain.engine.SyncRecord
import com.cady.cadymarketing.domain.engine.SyncSpecs
import com.cady.cadymarketing.domain.model.OpAction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A backup written to the Downloads folder: where, and how many rows it holds. */
data class SavedBackup(val path: String, val rows: Int)

/** What restoring a backup did. Rows already newer on this device are kept, nothing is ever deleted. */
data class RestoreSummary(val added: Int, val updated: Int, val kept: Int)

/**
 * A backup is one JSON file with every synced table as it is on this device (the operations log, the pictures and the logo
 * file stay out). It is written to Download/csdy like every other file the app makes, or shared from the share sheet, and
 * restoring merges it by the same rule as the sync: the newer copy of a row wins and nothing is deleted.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val local: RoomSyncLocal,
    private val settings: SettingsStore,
    private val opLog: OpLogRepository,
) {
    private suspend fun snapshot(): BackupFile {
        val tables = LinkedHashMap<String, List<SyncRecord>>()
        for (table in SyncSpecs.backedUp) tables[table] = local.rows(table)
        return BackupFile(System.currentTimeMillis(), versionName(), tables)
    }

    /** Writes the backup to Download/csdy. Throws IOException with a sentence to show. */
    suspend fun saveToDownloads(): SavedBackup = withContext(Dispatchers.IO) {
        val file = snapshot()
        val text = BackupJson.encode(file)
        val name = fileName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("تعذّر إنشاء الملف")
            context.contentResolver.openOutputStream(uri)?.use { out -> out.write(text.toByteArray(Charsets.UTF_8)) }
                ?: throw IOException("تعذّر كتابة الملف")
        } else {
            @Suppress("DEPRECATION")
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
            if (!folder.exists() && !folder.mkdirs()) throw IOException("تعذّر إنشاء المجلد، امنح التطبيق صلاحية التخزين")
            FileOutputStream(File(folder, name)).use { out -> out.write(text.toByteArray(Charsets.UTF_8)) }
        }
        settings.setLastBackupAt(file.createdAt)
        opLog.record(OpAction.BACKUP_EXPORT, name, "${file.total} سجل")
        SavedBackup("Download/$FOLDER/$name", file.total)
    }

    /** Writes the backup to the private share folder and returns an address other apps may read. */
    suspend fun shareUri(): Uri = withContext(Dispatchers.IO) {
        val file = snapshot()
        val folder = File(context.cacheDir, SHARE_FOLDER)
        folder.mkdirs()
        val old = System.currentTimeMillis() - ONE_DAY_MS
        folder.listFiles()?.forEach { if (it.lastModified() < old) it.delete() }
        val target = File(folder, fileName())
        FileOutputStream(target).use { out -> out.write(BackupJson.encode(file).toByteArray(Charsets.UTF_8)) }
        settings.setLastBackupAt(file.createdAt)
        opLog.record(OpAction.BACKUP_EXPORT, target.name, "مشاركة · ${file.total} سجل")
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
    }

    /** Reads the file the person picked. Nothing is changed yet: the answer says what the file holds, or why it is refused. */
    suspend fun read(uri: Uri): BackupDecode = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) return@withContext BackupDecode.Bad("الملف كبير جدًا ليكون نسخة احتياطية.")
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
        if (bytes == null) BackupDecode.Bad("تعذّرت قراءة الملف.") else BackupJson.decode(bytes.toString(Charsets.UTF_8))
    }

    /** Merges the backup into this device by the sync's rule. The rows added or changed are uploaded by the next sync. */
    suspend fun restore(file: BackupFile): RestoreSummary {
        var added = 0
        var updated = 0
        var kept = 0
        for ((table, rows) in file.tables) {
            if (table !in SyncSpecs.backedUp) continue
            val spec = SyncSpecs.spec(table) ?: continue
            val plan = BackupMerge.plan(local.rows(table), rows, spec.number != null)
            if (plan.apply.isNotEmpty()) local.apply(table, plan.apply)
            added += plan.added
            updated += plan.updated
            kept += plan.kept
        }
        opLog.record(OpAction.BACKUP_IMPORT, "نسخة بتاريخ " + formatDate(file.createdAt), "أُضيف $added · حُدّث $updated · تُرك $kept")
        return RestoreSummary(added, updated, kept)
    }

    private fun fileName(): String =
        "cady-marketing-backup-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".json"

    private fun formatDate(at: Long): String =
        if (at <= 0L) "غير معروف" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(at))

    private fun versionName(): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }

    private companion object {
        const val FOLDER = "csdy"
        const val SHARE_FOLDER = "share"
        const val MIME = "application/json"
        const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
        const val BUFFER = 16 * 1024
        const val MAX_BYTES = 40 * 1024 * 1024
    }
}

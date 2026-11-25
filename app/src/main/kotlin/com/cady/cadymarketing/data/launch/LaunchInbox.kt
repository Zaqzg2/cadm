package com.cady.cadymarketing.data.launch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.IntentCompat
import com.cady.cadymarketing.data.media.PictureStore
import com.cady.cadymarketing.domain.engine.AppLaunch
import com.cady.cadymarketing.domain.engine.IncomingKind
import com.cady.cadymarketing.domain.engine.IncomingRules
import com.cady.cadymarketing.domain.engine.LaunchActions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.withContext

/**
 * Everything that opens the app from outside lands here first: a long-press shortcut on the launcher icon, a button of
 * the home-screen widget, another app's Share, or "Open with" on a picture or a JSON file.
 *
 * The activity hands every incoming intent to [accept]; the signed-in shell takes the request with [takePending] and
 * opens the right screen. A request that arrives before sign-in simply waits here. Shared pictures are copied into the
 * app at once (the permission to read them ends with the sender's task), and shared files into the cache.
 *
 * What a screen still has to pick up after the shell opened it (the Studio takes the shared picture or text, Settings
 * takes the shared file) is kept in a second slot, so a rotation or a recomposition never runs a request twice.
 */
@Singleton
class LaunchInbox @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pictures: PictureStore,
) {
    private val _pending = MutableStateFlow<AppLaunch?>(null)

    /** The request nobody has taken yet; the shell watches it. */
    val pending: StateFlow<AppLaunch?> = _pending.asStateFlow()

    @Volatile
    private var staged: AppLaunch? = null

    fun post(launch: AppLaunch) {
        _pending.value = launch
    }

    /** Takes the waiting request (so it runs once). Shared content stays staged for the screen that will use it. */
    fun takePending(): AppLaunch? {
        val launch = _pending.getAndUpdate { null } ?: return null
        if (launch is AppLaunch.SharedPicture || launch is AppLaunch.SharedText || launch is AppLaunch.SharedFile) {
            staged = launch
        }
        return launch
    }

    /** The Studio asks for what was shared to it: a picture or a text. Null when nothing is staged. */
    fun takeStagedPost(): AppLaunch? {
        val current = staged
        if (current is AppLaunch.SharedPicture || current is AppLaunch.SharedText) {
            staged = null
            return current
        }
        return null
    }

    /** Settings asks for the JSON file that was shared to the app. Null when nothing is staged. */
    fun takeStagedFile(): AppLaunch.SharedFile? {
        val current = staged as? AppLaunch.SharedFile ?: return null
        staged = null
        return current
    }

    /** Reads one incoming intent. Returns false when it is nothing this app handles (the ordinary launcher start included). */
    suspend fun accept(intent: Intent): Boolean {
        val action = intent.action ?: return false
        val own = LaunchActions.fromAction(action)
        if (own != null) {
            post(own)
            return true
        }
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE && action != Intent.ACTION_VIEW) return false
        acceptFromOutside(intent)
        return true
    }

    private suspend fun acceptFromOutside(intent: Intent) {
        val uris = streamsOf(intent)
        val first = uris.firstOrNull()
        val text = IncomingRules.cleanText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
        val type = IncomingRules.resolveType(intent.type, first?.let { typeOf(it) })
        val kind = IncomingRules.classify(type, first?.let { nameOf(it) }, hasStream = first != null, hasText = text != null)
        when (kind) {
            IncomingKind.PICTURE -> {
                val file = if (first == null) null else pictures.import(first)
                if (file == null) {
                    tell(IncomingRules.UNREADABLE_MESSAGE)
                } else {
                    post(AppLaunch.SharedPicture(file, extraPictures = uris.size - 1, text = text))
                }
            }
            IncomingKind.JSON_FILE -> {
                val path = if (first == null) null else copyToCache(first)
                if (path == null) {
                    tell(IncomingRules.UNREADABLE_MESSAGE)
                } else {
                    post(AppLaunch.SharedFile(path, first?.let { nameOf(it) }))
                }
            }
            IncomingKind.TEXT -> post(AppLaunch.SharedText(text.orEmpty()))
            IncomingKind.UNSUPPORTED -> tell(IncomingRules.UNSUPPORTED_MESSAGE)
        }
    }

    /** The files an intent carries: the one it opens, the one (or several) it shares, or whatever its clip holds. */
    private fun streamsOf(intent: Intent): List<Uri> {
        val found = ArrayList<Uri>()
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { found.add(it) }
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { found.add(it) }
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { found.addAll(it) }
        }
        if (found.isEmpty()) {
            val clip = intent.clipData
            if (clip != null) {
                for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let { found.add(it) }
            }
        }
        return found
    }

    private fun typeOf(uri: Uri): String? = try {
        context.contentResolver.getType(uri)
    } catch (e: Exception) {
        null
    }

    private fun nameOf(uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (column >= 0) return cursor.getString(column)
                    }
                }
            } catch (e: Exception) {
                // Fall through to the last part of the address.
            }
        }
        return uri.lastPathSegment
    }

    /** Copies a shared file into the cache (the sender's permission ends soon). Null when it cannot be read or is too large. */
    private suspend fun copyToCache(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val folder = File(context.cacheDir, INCOMING_FOLDER)
            folder.mkdirs()
            val old = System.currentTimeMillis() - ONE_DAY_MS
            folder.listFiles()?.forEach { if (it.lastModified() < old) it.delete() }
            val target = File(folder, "incoming-" + System.currentTimeMillis() + ".json")
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { out ->
                    val buffer = ByteArray(BUFFER)
                    var total = 0L
                    var fits = true
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FILE_BYTES) {
                            fits = false
                            break
                        }
                        out.write(buffer, 0, read)
                    }
                    fits
                }
            } ?: false
            if (copied) {
                target.absolutePath
            } else {
                target.delete()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun tell(message: String) {
        withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val INCOMING_FOLDER = "incoming"
        const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
        const val BUFFER = 16 * 1024
        const val MAX_FILE_BYTES = 8L * 1024L * 1024L
    }
}

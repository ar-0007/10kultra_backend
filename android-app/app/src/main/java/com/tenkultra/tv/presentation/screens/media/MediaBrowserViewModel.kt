package com.tenkultra.tv.presentation.screens.media

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject

data class MediaEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isMedia: Boolean
)

data class MediaBrowserUiState(
    val title: String = "Media Browser",
    val currentPath: String? = null, // null = at the list of storage roots
    val entries: List<MediaEntry> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class MediaBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaBrowserUiState())
    val uiState = _uiState.asStateFlow()

    fun loadRoots() {
        val roots = LinkedHashMap<String, MediaEntry>()
        runCatching {
            val sm = context.getSystemService(StorageManager::class.java)
            sm?.storageVolumes?.forEach { vol ->
                val dir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vol.directory
                else runCatching {
                    File(vol.javaClass.getMethod("getPath").invoke(vol) as String)
                }.getOrNull()
                if (dir != null && dir.canRead()) {
                    val label = runCatching { vol.getDescription(context) }.getOrNull()
                        ?: if (vol.isRemovable) "USB / SD" else "Internal storage"
                    roots[dir.absolutePath] = MediaEntry(label, dir.absolutePath, true, false)
                }
            }
        }
        // Fallbacks for boxes that expose USB only via /storage or /mnt.
        listOf("/storage", "/mnt/media_rw", "/mnt/usb").forEach { base ->
            runCatching {
                File(base).listFiles()?.forEach { f ->
                    if (f.isDirectory && f.canRead() && f.name !in setOf("self", "emulated", "enc_emulated")) {
                        roots.putIfAbsent(f.absolutePath, MediaEntry(f.name, f.absolutePath, true, false))
                    }
                }
            }
        }
        runCatching {
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory()?.let {
                roots.putIfAbsent(it.absolutePath, MediaEntry("Internal storage", it.absolutePath, true, false))
            }
        }

        _uiState.update {
            it.copy(
                title = "Media Browser",
                currentPath = null,
                entries = roots.values.toList(),
                error = if (roots.isEmpty()) "No readable storage found. Attach a USB drive." else null
            )
        }
    }

    fun open(entry: MediaEntry) {
        if (!entry.isDirectory) return
        listDirectory(File(entry.path))
    }

    fun goUp(): Boolean {
        val current = _uiState.value.currentPath ?: return false // already at roots
        val parent = File(current).parentFile
        val isRoot = _uiState.value.entries.isEmpty() // unused
        // If parent is a known root boundary or unreadable, go back to roots list.
        if (parent == null || !parent.canRead() || current == "/storage" || current == "/") {
            loadRoots()
            return true
        }
        listDirectory(parent)
        return true
    }

    private fun listDirectory(dir: File) {
        val files = runCatching { dir.listFiles() }.getOrNull()
        if (files == null) {
            _uiState.update {
                it.copy(currentPath = dir.absolutePath, entries = emptyList(),
                    error = "Can't read this folder (permission or empty).", title = dir.name)
            }
            return
        }
        val entries = files
            .filter { !it.isHidden }
            .map { MediaEntry(it.name, it.absolutePath, it.isDirectory, it.isMediaFile()) }
            .filter { it.isDirectory || it.isMedia }
            .sortedWith(compareByDescending<MediaEntry> { it.isDirectory }.thenBy { it.name.lowercase() })

        _uiState.update {
            it.copy(
                title = dir.name.ifEmpty { dir.absolutePath },
                currentPath = dir.absolutePath,
                entries = entries,
                error = if (entries.isEmpty()) "No media files in this folder." else null
            )
        }
    }

    private fun File.isMediaFile(): Boolean {
        val ext = extension.lowercase()
        return ext in MEDIA_EXTENSIONS
    }

    companion object {
        private val MEDIA_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "ts", "m3u8", "webm", "mov", "flv", "wmv", "mpg", "mpeg", "3gp", "m4v",
            "mp3", "aac", "flac", "wav", "ogg", "m4a", "opus"
        )
    }
}

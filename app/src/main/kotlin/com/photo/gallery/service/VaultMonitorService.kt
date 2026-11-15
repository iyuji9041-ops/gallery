package com.photo.gallery.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import android.webkit.MimeTypeMap
import com.photo.gallery.data.local.AppDatabase
import com.photo.gallery.data.local.model.HiddenMediaEntity
import com.photo.gallery.data.vault.VaultManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background Service listener that monitors file system changes in the 'Hidden Vault'
 * directory to ensure that hidden status remains consistent even if files are added,
 * deleted, or moved manually by other apps or file managers.
 *
 * Key Capabilities:
 * 1. Real-time FileObserver for internal and external vault directories.
 * 2. Instant .nomedia file restoration if removed or tampered with by other apps.
 * 3. Automatic Room database synchronization for new files added manually to the vault.
 * 4. Automatic cleanup of database records if files are moved away or deleted externally.
 * 5. MediaScanner synchronization ensuring Android MediaStore unindexes any vault files.
 */
class VaultMonitorService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var fileObservers: List<FileObserver> = emptyList()
    private lateinit var database: AppDatabase
    private lateinit var vaultManager: VaultManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VaultMonitorService starting file system listener")
        database = AppDatabase.getInstance(applicationContext)
        vaultManager = VaultManager(applicationContext, database.securityDao())

        startDirectoryObservers()
        _isMonitoringActive.value = true

        // Initial reconciliation to synchronize any changes made while offline
        serviceScope.launch {
            reconcileVaultDirectories()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RECONCILE -> {
                serviceScope.launch {
                    reconcileVaultDirectories()
                }
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VaultMonitorService stopping file system listener")
        stopDirectoryObservers()
        serviceScope.cancel()
        _isMonitoringActive.value = false
    }

    private fun startDirectoryObservers() {
        stopDirectoryObservers()

        val directories = vaultManager.getAllVaultDirectories()
        val observers = mutableListOf<FileObserver>()

        for (directory in directories) {
            if (!directory.exists()) {
                directory.mkdirs()
            }
            VaultManager.ensureNoMedia(directory)

            val observer = createDirectoryObserver(directory)
            try {
                observer.startWatching()
                observers.add(observer)
                Log.d(TAG, "Monitoring vault directory: ${directory.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to monitor directory ${directory.absolutePath}", e)
            }
        }

        fileObservers = observers
    }

    private fun stopDirectoryObservers() {
        for (observer in fileObservers) {
            try {
                observer.stopWatching()
            } catch (e: Exception) {
                // Ignore
            }
        }
        fileObservers = emptyList()
    }

    private fun createDirectoryObserver(directory: File): FileObserver {
        val mask = FileObserver.CREATE or
                FileObserver.DELETE or
                FileObserver.MOVED_TO or
                FileObserver.MOVED_FROM or
                FileObserver.CLOSE_WRITE or
                FileObserver.ATTRIB or
                FileObserver.MODIFY

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(directory, mask) {
                override fun onEvent(event: Int, path: String?) {
                    handleFileEvent(directory, event, path)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(directory.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    handleFileEvent(directory, event, path)
                }
            }
        }
    }

    private fun handleFileEvent(directory: File, event: Int, path: String?) {
        if (path == null) {
            // Directory-level event: ensure .nomedia remains present
            VaultManager.ensureNoMedia(directory)
            return
        }

        val file = File(directory, path)
        val isNoMediaFile = path == ".nomedia"

        // 1. Critical .nomedia preservation
        if (isNoMediaFile) {
            if ((event and (FileObserver.DELETE or FileObserver.MOVED_FROM)) != 0 || !file.exists()) {
                Log.w(TAG, ".nomedia was deleted or moved from ${directory.name}! Restoring immediately.")
                VaultManager.ensureNoMedia(directory)
            }
            return
        }

        // 2. Handle files added or moved into vault by other apps
        if ((event and (FileObserver.CREATE or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE)) != 0) {
            serviceScope.launch {
                onFileAddedOrModified(directory, file)
            }
        }

        // 3. Handle files removed or moved out of vault by other apps
        if ((event and (FileObserver.DELETE or FileObserver.MOVED_FROM)) != 0) {
            serviceScope.launch {
                onFileRemoved(file)
            }
        }
    }

    private suspend fun onFileAddedOrModified(directory: File, file: File) = withContext(Dispatchers.IO) {
        try {
            // Guard: ensure .nomedia always exists
            VaultManager.ensureNoMedia(directory)

            if (!file.exists() || !file.isFile || file.name == ".nomedia") return@withContext

            val dao = database.securityDao()
            val existing = dao.getHiddenByVaultPath(file.absolutePath)

            if (existing == null) {
                val mimeType = resolveMimeType(file)
                val entity = HiddenMediaEntity(
                    originalPath = file.absolutePath,
                    vaultPath = file.absolutePath,
                    displayName = file.name,
                    mimeType = mimeType,
                    size = file.length(),
                    dateAdded = file.lastModified() / 1000L,
                    dateHidden = System.currentTimeMillis()
                )
                dao.insertHiddenMedia(entity)
                Log.i(TAG, "External file added to Hidden Vault, registered: ${file.name}")

                // Notify MediaScanner so Android MediaStore unindexes/purges this file
                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(file.absolutePath),
                    null
                ) { scannedPath, uri ->
                    Log.d(TAG, "MediaStore unindexed vault file: $scannedPath (uri: $uri)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling file add event for ${file.name}", e)
        }
    }

    private suspend fun onFileRemoved(file: File) = withContext(Dispatchers.IO) {
        try {
            if (file.name == ".nomedia") return@withContext

            // If the file no longer exists on disk, remove its DB entry
            if (!file.exists()) {
                val dao = database.securityDao()
                dao.deleteHiddenByVaultPath(file.absolutePath)
                Log.i(TAG, "External file removed from Hidden Vault, cleaned from database: ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling file remove event for ${file.name}", e)
        }
    }

    /**
     * Performs a full reconciliation scan across all vault directories.
     * Guarantees that any manual additions or movements by other apps are synced.
     */
    suspend fun reconcileVaultDirectories() = withContext(Dispatchers.IO) {
        try {
            val dao = database.securityDao()
            val directories = vaultManager.getAllVaultDirectories()

            for (directory in directories) {
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                VaultManager.ensureNoMedia(directory)

                // 1. Files on disk
                val filesOnDisk = directory.listFiles()
                    ?.filter { it.isFile && it.name != ".nomedia" }
                    ?: emptyList()

                val diskPathMap = filesOnDisk.associateBy { it.absolutePath }

                // 2. Register any file on disk that is missing in database
                for (file in filesOnDisk) {
                    val existing = dao.getHiddenByVaultPath(file.absolutePath)
                    if (existing == null) {
                        val mimeType = resolveMimeType(file)
                        val entity = HiddenMediaEntity(
                            originalPath = file.absolutePath,
                            vaultPath = file.absolutePath,
                            displayName = file.name,
                            mimeType = mimeType,
                            size = file.length(),
                            dateAdded = file.lastModified() / 1000L,
                            dateHidden = System.currentTimeMillis()
                        )
                        dao.insertHiddenMedia(entity)
                        Log.i(TAG, "Reconciled missing vault item: ${file.name}")

                        MediaScannerConnection.scanFile(
                            applicationContext,
                            arrayOf(file.absolutePath),
                            null,
                            null
                        )
                    }
                }

                // 3. Remove any database records whose physical file is gone
                val dbEntities = dao.getAllHiddenList().filter { it.vaultPath.startsWith(directory.absolutePath) }
                for (entry in dbEntities) {
                    if (!diskPathMap.containsKey(entry.vaultPath) || !File(entry.vaultPath).exists()) {
                        dao.deleteHiddenMediaById(entry.id)
                        Log.i(TAG, "Pruned deleted vault item from DB: ${entry.displayName}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during vault reconciliation", e)
        }
    }

    private fun resolveMimeType(file: File): String {
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!mime.isNullOrEmpty()) return mime

        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            else -> "image/jpeg"
        }
    }

    companion object {
        private const val TAG = "VaultMonitorService"
        const val ACTION_RECONCILE = "com.photo.gallery.action.RECONCILE_VAULT"
        const val ACTION_STOP = "com.photo.gallery.action.STOP_VAULT_MONITOR"

        private val _isMonitoringActive = MutableStateFlow(false)
        val isMonitoringActive: StateFlow<Boolean> = _isMonitoringActive.asStateFlow()

        /**
         * Safely starts the background VaultMonitorService.
         */
        fun start(context: Context) {
            try {
                val intent = Intent(context, VaultMonitorService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start VaultMonitorService: ${e.message}")
            }
        }

        /**
         * Triggers an immediate file system scan and database reconciliation.
         */
        fun triggerReconciliation(context: Context) {
            try {
                val intent = Intent(context, VaultMonitorService::class.java).apply {
                    action = ACTION_RECONCILE
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to trigger vault reconciliation: ${e.message}")
            }
        }

        /**
         * Stops the background monitor service.
         */
        fun stop(context: Context) {
            try {
                val intent = Intent(context, VaultMonitorService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to stop VaultMonitorService: ${e.message}")
            }
        }
    }
}

package com.silas.omaster.xposed

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 魔改版 Root 权限管理器 (专治 ColorOS 15 XML)
 * 核心逻辑：打晕保安！跳过所有 MMKV 文件夹的检查和拷贝，直接放行！
 */
class RootManager private constructor() {

    enum class RootStatus {
        Unknown, Available, Unavailable, Denied
    }

    private val _rootStatus = MutableStateFlow(RootStatus.Unknown)
    val rootStatus: StateFlow<RootStatus> = _rootStatus.asStateFlow()

    @Volatile
    private var shellConfigured = false

    private fun ensureShellConfigured() {
        if (shellConfigured) return
        try {
            Shell.enableVerboseLogging = true
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(15)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Shell.Builder 配置降级处理: ${e.message}")
        }
        shellConfigured = true
    }

    suspend fun checkRoot(): RootStatus = withContext(Dispatchers.IO) {
        ensureShellConfigured()
        try {
            when (Shell.isAppGrantedRoot()) {
                true -> RootStatus.Available.also { _rootStatus.value = it }
                false -> RootStatus.Denied.also { _rootStatus.value = it }
                null -> fallbackRootCheck()
            }
        } catch (e: Exception) {
            fallbackRootCheck()
        }
    }

    private fun fallbackRootCheck(): RootStatus {
        return try {
            val result = Shell.cmd("id").exec()
            if (result.isSuccess && result.out.any { "uid=0" in it }) {
                RootStatus.Available
            } else {
                RootStatus.Unavailable
            }
        } catch (e: Exception) {
            RootStatus.Unavailable
        }.also { _rootStatus.value = it }
    }

    suspend fun stopCameraApp(): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("am force-stop $CAMERA_PACKAGE").exec().isSuccess
    }

    suspend fun killCameraApp(): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("killall $CAMERA_PACKAGE").exec().isSuccess
    }

    suspend fun readFilterMapJson(): String? = withContext(Dispatchers.IO) {
        val result = Shell.cmd("cat $CAMERA_DATA_DIR/files/$FILTER_MAP_FILE").exec()
        if (result.isSuccess) {
            result.out.joinToString("\n")
        } else {
            null
        }
    }

    // ==========================================
    // 以下是核心魔改区：架空原来的 MMKV 文件操作！
    // ==========================================

    suspend fun copyMmkvToTemp(tempDir: String, targetFile: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "魔改版放行：跳过 MMKV 拷贝，将直接由 XML Writer 处理写入！")
        true // 直接返回成功
    }

    suspend fun backupMmkv(backupDir: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "魔改版放行：跳过 MMKV 备份")
        true
    }

    suspend fun writeMmkvBack(tempDir: String, targetFile: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "魔改版放行：跳过 MMKV 写回，XML 已经在 Writer 中直写完成了！")
        true
    }

    suspend fun restoreMmkvFromBackup(backupDir: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "魔改版放行：跳过 MMKV 恢复")
        true
    }

    suspend fun cleanupTempDir(tempDir: String) = withContext(Dispatchers.IO) {
        Shell.cmd("rm -rf '$tempDir'").exec()
    }

    companion object {
        private const val TAG = "OMaster-RootManager"
        private const val CAMERA_PACKAGE = "com.oplus.camera"
        private const val CAMERA_DATA_DIR = "/data/data/$CAMERA_PACKAGE"
        private const val FILTER_MAP_FILE = "omaster_filter_map.json"

        @Volatile
        private var instance: RootManager? = null

        fun getInstance(): RootManager {
            return instance ?: synchronized(this) {
                instance ?: RootManager().also { instance = it }
            }
        }
    }
}

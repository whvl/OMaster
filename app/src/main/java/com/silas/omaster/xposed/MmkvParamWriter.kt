package com.silas.omaster.xposed

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File

class MmkvParamWriter(private val context: Context) {

    fun writeParams(
        tempDir: String,
        fileName: String,
        params: Map<String, Int>
    ): Boolean {
        return try {
            val targetXmlPath = "/data/data/com.oplus.camera/shared_prefs/com.oplus.camera_preferences_0.xml"
            
            // 绝杀第 1 步：先解除内核锁 + 强杀相机幽灵！
            Shell.cmd(
                "chattr -i '$targetXmlPath'",
                "am force-stop com.oplus.camera"
            ).exec()
            
            val readResult = Shell.cmd("cat '$targetXmlPath'").exec()
            if (!readResult.isSuccess) return false
            
            var xmlContent = readResult.out.joinToString("\n")
            
            // 绝杀第 2 步：精准注入滤镜参数（保留滤镜专属的小尾巴）
            params.forEach { (key, value) ->
                val regex = Regex("""<int\s+name="$key"\s+value="[^"]*"\s*/>""")
                if (xmlContent.contains(regex)) {
                    xmlContent = xmlContent.replace(regex, """<int name="$key" value="$value" />""")
                } else {
                    val insertString = """    <int name="$key" value="$value" />""" + "\n</map>"
                    xmlContent = xmlContent.replace("</map>", insertString)
                }
            }
            
            // 绝杀第 3 步：强行洗刷专业参数，全部重置为 auto（自动）！
            val autoStringParams = listOf(
                "pref_professional_iso_key",           // 专业模式 ISO
                "pref_professional_shutter_key",       // 专业模式快门
                "pref_professional_whitebalance_key",  // 专业模式白平衡
                "pref_professional_focus_mode_key",    // 专业模式对焦
                "pref_film_mode_iso",                  // 电影模式 ISO
                "pref_film_mode_shutter",              // 电影模式快门
                "pref_film_mode_white_balance"         // 电影模式白平衡
            )
            
            autoStringParams.forEach { autoKey ->
                val stringRegex = Regex("""<string\s+name="$autoKey">[^<]*</string>""")
                if (xmlContent.contains(stringRegex)) {
                    xmlContent = xmlContent.replace(stringRegex, """<string name="$autoKey">auto</string>""")
                } else {
                    val insertAutoString = """    <string name="$autoKey">auto</string>""" + "\n</map>"
                    xmlContent = xmlContent.replace("</map>", insertAutoString)
                }
            }
            
            // 绝杀第 4 步：写入临时文件并获取原文件权限
            val localTempFile = File(context.cacheDir, "temp_xml.xml")
            localTempFile.writeText(xmlContent)
            
            val uidResult = Shell.cmd("stat -c '%u:%g' '$targetXmlPath'").exec()
            val owner = uidResult.out.firstOrNull()?.trim() ?: "1000:1000"
            
            // 绝杀第 5 步：写回文件，修复权限，并立刻打上内核死锁！
            val writeResult = Shell.cmd(
                "cp -a '${localTempFile.absolutePath}' '$targetXmlPath'",
                "chown $owner '$targetXmlPath'",
                "chmod 660 '$targetXmlPath'",
                "chattr +i '$targetXmlPath'"
            ).exec()
            
            if (writeResult.isSuccess) {
                Log.d(TAG, "完美注入！滤镜已更新，专业参数已重置为 Auto，并已锁死！")
                return true
            } else {
                Log.e(TAG, "写入失败: ${writeResult.err.joinToString()}")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "异常", e)
            return false
        }
    }

    fun readParams(tempDir: String, fileName: String, keys: List<String>, defaultValue: Int = 0): Map<String, Int> = emptyMap()
    fun getAllKeys(tempDir: String, fileName: String): Array<String> = emptyArray()

    companion object {
        private const val TAG = "OMaster-XmlWriter"
    }
}

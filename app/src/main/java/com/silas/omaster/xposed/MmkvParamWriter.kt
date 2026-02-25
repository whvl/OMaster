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
            // 不管原 App 要求写到哪，在 ColorOS 15 上，强制统统写进这个 XML 里！
            val targetXmlPath = "/data/data/com.oplus.camera/shared_prefs/com.oplus.camera_preferences_0.xml"
            
            // 第一步：先强杀相机进程，彻底清除内存幽灵
            Shell.cmd("am force-stop com.oplus.camera").exec()
            
            // 第二步：用 Root 读取真实的 XML 内容
            val readResult = Shell.cmd("cat '$targetXmlPath'").exec()
            if (!readResult.isSuccess) {
                Log.e(TAG, "读取 XML 失败")
                return false
            }
            
            var xmlContent = readResult.out.joinToString("\n")
            
            // 第三步：用正则精准替换你的滤镜参数 (⚠️ 已经拔掉了斩尾刀！)
            // 老老实实保留 _18 这种后缀，精准注入到对应的滤镜槽位里！
            params.forEach { (key, value) ->
                val regex = Regex("""<int\s+name="$key"\s+value="[^"]*"\s*/>""")
                if (xmlContent.contains(regex)) {
                    // 如果文件里已经有这个槽位的数据，直接覆盖替换
                    xmlContent = xmlContent.replace(regex, """<int name="$key" value="$value" />""")
                } else {
                    // 如果没有，就补在文件最后
                    val insertString = """    <int name="$key" value="$value" />""" + "\n</map>"
                    xmlContent = xmlContent.replace("</map>", insertString)
                }
            }
            
            // 第四步：把改好的文本暂存到本地
            val localTempFile = File(context.cacheDir, "temp_xml.xml")
            localTempFile.writeText(xmlContent)
            
            // 第五步：获取相机文件的原始权限主人
            val uidResult = Shell.cmd("stat -c '%u:%g' '$targetXmlPath'").exec()
            val owner = uidResult.out.firstOrNull()?.trim() ?: "1000:1000"
            
            // 第六步：用 Root 权限暴力覆盖，并恢复权限 (注：这个版本没加内核锁 chattr，方便你后续自己调试)
            val writeResult = Shell.cmd(
                "cp -a '${localTempFile.absolutePath}' '$targetXmlPath'",
                "chown $owner '$targetXmlPath'",
                "chmod 660 '$targetXmlPath'"
            ).exec()
            
            if (writeResult.isSuccess) {
                Log.d(TAG, "XML 暴力写入并覆盖成功！共处理 ${params.size} 个参数！")
                return true
            } else {
                Log.e(TAG, "写入彻底失败: ${writeResult.err.joinToString()}")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "代码执行异常", e)
            return false
        }
    }

    fun readParams(tempDir: String, fileName: String, keys: List<String>, defaultValue: Int = 0): Map<String, Int> = emptyMap()
    fun getAllKeys(tempDir: String, fileName: String): Array<String> = emptyArray()

    companion object {
        private const val TAG = "OMaster-XmlWriter"
    }
}

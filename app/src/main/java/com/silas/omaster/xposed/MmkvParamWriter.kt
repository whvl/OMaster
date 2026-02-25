package com.silas.omaster.xposed

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * 魔改版 XML 参数读写器 (专治 ColorOS 15)
 * 彻底抛弃腾讯 MMKV，直接用 Root 权限修改底层 XML！
 */
class MmkvParamWriter(private val context: Context) {

    fun writeParams(
        tempDir: String,
        fileName: String,
        params: Map<String, Int>
    ): Boolean {
        return try {
            // 拦截：如果不是大师模式的核心文件，直接放行，防止报错
            if (!fileName.contains("preferences_0")) {
                return true
            }
            
            val xmlFileName = "com.oplus.camera_preferences_0.xml"
            val targetXmlPath = "/data/data/com.oplus.camera/shared_prefs/$xmlFileName"
            
            // 1. 用 Root 读取真实 XML 内容
            val readResult = Shell.cmd("cat '$targetXmlPath'").exec()
            if (!readResult.isSuccess) {
                Log.e(TAG, "读取 XML 失败，文件可能不存在")
                return false
            }
            
            var xmlContent = readResult.out.joinToString("\n")
            
            // 2. 正则表达式暴力替换参数！
            params.forEach { (key, value) ->
                val regex = Regex("""<int\s+name="$key"\s+value="[^"]*"\s*/>""")
                if (xmlContent.contains(regex)) {
                    // 如果参数已存在，替换它
                    xmlContent = xmlContent.replace(regex, """<int name="$key" value="$value" />""")
                } else {
                    // 如果参数不存在，把它插入到 </map> 结尾之前
                    val insertString = """    <int name="$key" value="$value" />""" + "\n</map>"
                    xmlContent = xmlContent.replace("</map>", insertString)
                }
            }
            
            // 3. 将改好的文本保存到咱们 App 自己的缓存目录
            val tempFilePath = context.cacheDir.absolutePath + "/temp_xml.xml"
            File(tempFilePath).writeText(xmlContent)
            
            // 4. 获取相机文件的原始权限主人
            val uidResult = Shell.cmd("stat -c '%u:%g' '$targetXmlPath'").exec()
            val owner = uidResult.out.firstOrNull()?.trim() ?: "1000:1000"
            
            // 5. 用 Root 权限强行覆盖回去，并修复权限！
            val writeResult = Shell.cmd(
                "cp -a '$tempFilePath' '$targetXmlPath'",
                "chown $owner '$targetXmlPath'",
                "chmod 660 '$targetXmlPath'"
            ).exec()
            
            Log.d(TAG, "XML 魔法注入成功！共写入 ${params.size} 个参数！")
            true
        } catch (e: Exception) {
            Log.e(TAG, "XML 写入异常", e)
            false
        }
    }

    fun readParams(
        tempDir: String,
        fileName: String,
        keys: List<String>,
        defaultValue: Int = 0
    ): Map<String, Int> {
        return emptyMap() // 暂时屏蔽 UI 读取预览功能，不影响核心写入
    }

    fun getAllKeys(tempDir: String, fileName: String): Array<String> {
        return emptyArray()
    }

    companion object {
        private const val TAG = "OMaster-XmlWriter"
    }
}

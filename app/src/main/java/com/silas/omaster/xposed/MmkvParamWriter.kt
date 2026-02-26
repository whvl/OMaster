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
            
            // 第一步：先强杀相机进程，彻底清除内存幽灵
            Shell.cmd("am force-stop com.oplus.camera").exec()
            
            // 第二步：用 Root 读取真实的 XML 内容
            val readResult = Shell.cmd("cat '$targetXmlPath'").exec()
            if (!readResult.isSuccess) {
                Log.e(TAG, "读取 XML 失败")
                return false
            }
            
            var xmlContent = readResult.out.joinToString("\n")
            
            // 第三步：用正则精准替换调色参数（保留你的斩尾刀，强行修改当前全局参数）
            params.forEach { (originalKey, value) ->
                val key = originalKey.replace(Regex("""_\d+$"""), "")
                
                // 修改常规的 int 参数
                val regex = Regex("""<int\s+name="$key"\s+value="[^"]*"\s*/>""")
                if (xmlContent.contains(regex)) {
                    xmlContent = xmlContent.replace(regex, """<int name="$key" value="$value" />""")
                } else {
                    val insertString = """    <int name="$key" value="$value" />""" + "\n</map>"
                    xmlContent = xmlContent.replace("</map>", insertString)
                }
                
                // 【终极联动】：如果代码正在写入滤镜序号，咱们同步修改 UI 上的滤镜选框！
                if (key == "key_master_mode_effect_filter") {
                    val filterRegex = Regex("""<int\s+name="key_professional_filter_index"\s+value="[^"]*"\s*/>""")
                    if (xmlContent.contains(filterRegex)) {
                        xmlContent = xmlContent.replace(filterRegex, """<int name="key_professional_filter_index" value="$value" />""")
                    } else {
                        val insertFilter = """    <int name="key_professional_filter_index" value="$value" />""" + "\n</map>"
                        xmlContent = xmlContent.replace("</map>", insertFilter)
                    }
                }
            }
            
            // 第四步：强行洗刷专业参数，用你扒出来的密码本重置为安全默认值！
            val autoStringParams = mapOf(
                "pref_professional_whitebalance_key" to "auto",
                "pref_professional_focus_mode_key" to "auto",
                "pref_professional_exposure_time_key" to "-1", // 自动快门
                "pref_professional_exposure_compensation_key" to "8", // EV曝光补偿归0
                "pref_professional_iso_key" to "auto"
            )
            
            autoStringParams.forEach { (autoKey, autoValue) ->
                val stringRegex = Regex("""<string\s+name="$autoKey">[^<]*</string>""")
                if (xmlContent.contains(stringRegex)) {
                    xmlContent = xmlContent.replace(stringRegex, """<string name="$autoKey">$autoValue</string>""")
                } else {
                    val insertAutoString = """    <string name="$autoKey">$autoValue</string>""" + "\n</map>"
                    xmlContent = xmlContent.replace("</map>", insertAutoString)
                }
            }
            
            // 第五步：把改好的文本暂存到本地
            val localTempFile = File(context.cacheDir, "temp_xml.xml")
            localTempFile.writeText(xmlContent)
            
            // 第六步：获取相机文件的原始权限主人
            val uidResult = Shell.cmd("stat -c '%u:%g' '$targetXmlPath'").exec()
            val owner = uidResult.out.firstOrNull()?.trim() ?: "1000:1000"
            
            // 第七步：用 Root 权限暴力覆盖，并恢复权限
            val writeResult = Shell.cmd(
                "cp -a '${localTempFile.absolutePath}' '$targetXmlPath'",
                "chown $owner '$targetXmlPath'",
                "chmod 660 '$targetXmlPath'"
            ).exec()
            
            if (writeResult.isSuccess) {
                Log.d(TAG, "XML 暴力写入、同步滤镜 UI、重置专业参数全部成功！")
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

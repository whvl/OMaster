package com.silas.omaster.ui.create

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.silas.omaster.data.repository.PresetRepository
import com.silas.omaster.model.MasterPreset
import com.silas.omaster.model.PresetItem
import com.silas.omaster.model.PresetSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * 通用预设编辑器 ViewModel
 * 支持基于 sections 的灵活配置
 */
class UniversalCreatePresetViewModel(
    private val context: Context,
    private val repository: PresetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UniversalPresetUiState())
    val uiState: StateFlow<UniversalPresetUiState> = _uiState.asStateFlow()
    
    private var isLoaded = false
    private var editingPresetId: String? = null

    // 加载模版或者现有预设
    fun loadTemplate(presetId: String?) {
        if (isLoaded) return
        isLoaded = true
        editingPresetId = null // Ensure not in edit mode
        
        if (presetId == null) {
            // 从零开始
            _uiState.value = UniversalPresetUiState()
            return
        }

        viewModelScope.launch {
            val preset = repository.getPresetById(presetId)
            if (preset != null) {
                // 如果是旧数据结构，转换为新结构
                val sections = if (preset.sections.isNullOrEmpty()) {
                    convertOldPresetToSections(preset)
                } else {
                    preset.sections
                }
                
                _uiState.value = UniversalPresetUiState(
                    name = if (preset.isCustom) preset.name else "${preset.name} (Copy)",
                    sections = sections,
                    // Template mode: require new image
                    imageUri = null,
                    isEditMode = false
                )
            }
        }
    }

    // Load preset for editing
    fun loadPresetForEdit(presetId: String) {
        if (isLoaded) return
        isLoaded = true
        editingPresetId = presetId
        
        viewModelScope.launch {
            val preset = repository.getPresetById(presetId)
            if (preset != null) {
                val sections = if (preset.sections.isNullOrEmpty()) {
                    convertOldPresetToSections(preset)
                } else {
                    preset.sections
                }
                
                _uiState.value = UniversalPresetUiState(
                    name = preset.name,
                    sections = sections,
                    imageUri = null, // Will use originalCoverPath
                    originalCoverPath = preset.coverPath,
                    isEditMode = true
                )
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateImageUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(imageUri = uri)
    }

    fun addSection(title: String) {
        val newSection = PresetSection(title = title, items = emptyList())
        val currentSections = _uiState.value.sections.toMutableList()
        currentSections.add(newSection)
        _uiState.value = _uiState.value.copy(sections = currentSections)
    }

    fun removeSection(index: Int) {
        val currentSections = _uiState.value.sections.toMutableList()
        if (index in currentSections.indices) {
            currentSections.removeAt(index)
            _uiState.value = _uiState.value.copy(sections = currentSections)
        }
    }

    fun addItemToSection(sectionIndex: Int, item: PresetItem) {
        val currentSections = _uiState.value.sections.toMutableList()
        if (sectionIndex in currentSections.indices) {
            val section = currentSections[sectionIndex]
            val newItems = section.items.toMutableList()
            newItems.add(item)
            currentSections[sectionIndex] = section.copy(items = newItems)
            _uiState.value = _uiState.value.copy(sections = currentSections)
        }
    }

    fun removeItemFromSection(sectionIndex: Int, itemIndex: Int) {
        val currentSections = _uiState.value.sections.toMutableList()
        if (sectionIndex in currentSections.indices) {
            val section = currentSections[sectionIndex]
            val newItems = section.items.toMutableList()
            if (itemIndex in newItems.indices) {
                newItems.removeAt(itemIndex)
                currentSections[sectionIndex] = section.copy(items = newItems)
                _uiState.value = _uiState.value.copy(sections = currentSections)
            }
        }
    }
    
    fun updateItemInSection(sectionIndex: Int, itemIndex: Int, newItem: PresetItem) {
        val currentSections = _uiState.value.sections.toMutableList()
        if (sectionIndex in currentSections.indices) {
            val section = currentSections[sectionIndex]
            val newItems = section.items.toMutableList()
            if (itemIndex in newItems.indices) {
                newItems[itemIndex] = newItem
                currentSections[sectionIndex] = section.copy(items = newItems)
                _uiState.value = _uiState.value.copy(sections = currentSections)
            }
        }
    }

    fun savePreset(): Boolean {
        val state = _uiState.value
        if (state.name.isBlank()) return false
        
        // Validation:
        // - Create mode: must have imageUri
        // - Edit mode: must have imageUri OR originalCoverPath
        if (state.imageUri == null && state.originalCoverPath == null) return false

        return try {
            val coverPath = if (state.imageUri != null) {
                saveImageToInternalStorage(state.imageUri)
            } else {
                state.originalCoverPath!!
            }
            
            val preset = MasterPreset(
                id = editingPresetId ?: UUID.randomUUID().toString(),
                name = state.name,
                coverPath = coverPath,
                author = "@用户自定义",
                sections = state.sections,
                isCustom = true
            )
            
            if (editingPresetId != null) {
                repository.updateCustomPreset(preset)
            } else {
                repository.addCustomPreset(preset)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun convertOldPresetToSections(preset: MasterPreset): List<PresetSection> {
        val items = mutableListOf<PresetItem>()
        
        // 尝试从旧字段提取数据
        preset.filter?.let { items.add(PresetItem("滤镜", it, 2)) }
        preset.softLight?.let { items.add(PresetItem("柔光", it, 1)) }
        preset.tone?.let { items.add(PresetItem("影调", it.toString(), 1)) }
        preset.saturation?.let { items.add(PresetItem("饱和度", it.toString(), 1)) }
        preset.warmCool?.let { items.add(PresetItem("冷暖", it.toString(), 1)) }
        preset.cyanMagenta?.let { items.add(PresetItem("青品", it.toString(), 1)) }
        preset.sharpness?.let { items.add(PresetItem("锐度", it.toString(), 1)) }
        preset.vignette?.let { items.add(PresetItem("暗角", it, 2)) }
        
        // Pro 模式参数
        preset.exposureCompensation?.let { items.add(PresetItem("曝光补偿", it, 1)) }
        preset.colorTemperature?.let { items.add(PresetItem("色温", it.toString(), 1)) }
        preset.colorHue?.let { items.add(PresetItem("色调", it.toString(), 1)) }
        
        return listOf(PresetSection("参数配置", items))
    }

    @Throws(IOException::class)
    private fun saveImageToInternalStorage(uri: Uri): String {
        val fileName = "custom_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, "presets/$fileName")
        file.parentFile?.mkdirs()
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IOException("无法打开图片文件")
        
        return "presets/$fileName"
    }
}

data class UniversalPresetUiState(
    val name: String = "",
    val imageUri: Uri? = null,
    val sections: List<PresetSection> = emptyList(),
    val originalCoverPath: String? = null,
    val isEditMode: Boolean = false
)

class UniversalCreatePresetViewModelFactory(
    private val context: Context,
    private val repository: PresetRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UniversalCreatePresetViewModel::class.java)) {
            return UniversalCreatePresetViewModel(context, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

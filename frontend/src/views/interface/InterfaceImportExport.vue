<template>
  <div class="import-export-page">
    <el-row :gutter="24">
      <!-- ─── 导出卡片 ────────────────────────────────────────────────── -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>配置导出</span>
          </template>
          <p style="margin: 0 0 16px; color: #666;">
            将当前系统中所有转换模板与可视化接口配置打包为 zip 文件下载。
          </p>
          <el-button
            type="primary"
            :loading="exporting"
            @click="handleExport"
          >
            <el-icon><Download /></el-icon>
            全量导出 zip
          </el-button>
        </el-card>
      </el-col>

      <!-- ─── 导入卡片 ────────────────────────────────────────────────── -->
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>配置导入</span>
          </template>

          <el-form label-width="100px">
            <el-form-item label="冲突策略">
              <el-radio-group v-model="strategy">
                <el-radio label="SKIP">跳过（保留现有）</el-radio>
                <el-radio label="OVERWRITE">覆盖（替换现有）</el-radio>
                <el-radio label="ASK">仅检测冲突</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="选择文件">
              <el-upload
                ref="uploadRef"
                :auto-upload="false"
                :limit="1"
                accept=".zip"
                :on-change="onFileChange"
                :on-exceed="onExceed"
              >
                <el-button>选择 zip 文件</el-button>
                <template #tip>
                  <div style="color: #999; font-size: 12px; margin-top: 4px;">
                    仅支持由本系统导出的 zip 文件
                  </div>
                </template>
              </el-upload>
            </el-form-item>
            <el-form-item>
              <el-button
                type="success"
                :loading="importing"
                :disabled="!selectedFile"
                @click="handleImport"
              >开始导入</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- ─── 导入结果 ────────────────────────────────────────────────────── -->
    <el-card v-if="importResult" style="margin-top: 24px;">
      <template #header>
        <span>导入结果</span>
      </template>

      <!-- 冲突列表（ASK 策略） -->
      <el-alert
        v-if="importResult.conflicts && importResult.conflicts.length > 0"
        type="warning"
        :title="`检测到 ${importResult.conflicts.length} 条冲突，请选择处理策略后重新导入`"
        show-icon
        :closable="false"
        style="margin-bottom: 16px;"
      />

      <el-row :gutter="16">
        <el-col :span="8" v-if="importResult.imported && importResult.imported.length > 0">
          <el-tag type="success" style="margin-bottom: 8px;">
            成功导入 {{ importResult.imported.length }} 条
          </el-tag>
          <ul class="result-list">
            <li v-for="(item, idx) in importResult.imported" :key="idx">{{ item }}</li>
          </ul>
        </el-col>
        <el-col :span="8" v-if="importResult.skipped && importResult.skipped.length > 0">
          <el-tag type="info" style="margin-bottom: 8px;">
            跳过 {{ importResult.skipped.length }} 条
          </el-tag>
          <ul class="result-list">
            <li v-for="(item, idx) in importResult.skipped" :key="idx">{{ item }}</li>
          </ul>
        </el-col>
        <el-col :span="8" v-if="importResult.failed && importResult.failed.length > 0">
          <el-tag type="danger" style="margin-bottom: 8px;">
            失败 {{ importResult.failed.length }} 条
          </el-tag>
          <ul class="result-list">
            <li v-for="(item, idx) in importResult.failed" :key="idx">{{ item }}</li>
          </ul>
        </el-col>
      </el-row>

      <div
        v-if="importResult.conflicts && importResult.conflicts.length > 0"
        style="margin-top: 12px;"
      >
        <p style="margin: 0 0 8px; font-weight: 600;">冲突列表：</p>
        <el-table :data="importResult.conflicts" border size="small">
          <el-table-column prop="type" label="类型" width="100" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="detail" label="详情" />
        </el-table>
      </div>

      <div
        v-if="(!importResult.imported || importResult.imported.length === 0)
          && (!importResult.conflicts || importResult.conflicts.length === 0)
          && (!importResult.failed || importResult.failed.length === 0)"
        style="color: #999;"
      >
        无可导入内容（zip 为空或全部跳过）
      </div>
    </el-card>

    <!-- ─── FN-11 Task 5/6 · Excel 导入导出（新版能力） ─── -->
    <el-card style="margin-top: 24px;">
      <template #header>
        <span>Excel 导入导出（新版）</span>
      </template>
      <p style="margin: 0 0 12px; color: #666;">
        每个接口/模板一个独立 xlsx 文件，可在 Excel 里直接编辑后回传；文件名前缀
        <code>QUERY_/INSERT_/UPDATE_/DELETE_</code>（接口）或 <code>TEMPLATE_</code>（模板）区分类型。
      </p>

      <!-- 简易 Excel/MD 导出 -->
      <el-form inline label-width="100px" style="margin-top: 12px;">
        <el-form-item label="ID 列表">
          <el-input v-model="excelExportIds" placeholder="如 1,2,3" style="width: 220px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="excelExportType" style="width: 140px">
            <el-option label="接口配置" value="interface" />
            <el-option label="转换模板" value="template" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button :loading="excelExporting" @click="doExportExcel">导出 Excel</el-button>
          <el-button :loading="mdExporting" @click="doExportMarkdown">导出 Markdown</el-button>
        </el-form-item>
      </el-form>

      <!-- 多 Excel 上传导入 -->
      <el-form label-width="100px" style="margin-top: 8px;">
        <el-form-item label="冲突策略">
          <el-radio-group v-model="excelStrategy">
            <el-radio label="SKIP">跳过（保留现有）</el-radio>
            <el-radio label="OVERWRITE">覆盖（替换现有）</el-radio>
            <el-radio label="ASK">仅检测冲突</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            ref="excelUploadRef"
            :auto-upload="false"
            multiple
            accept=".xlsx"
            :on-change="onExcelFileChange"
            :on-remove="onExcelFileRemove"
            :file-list="excelFileList"
          >
            <el-button>选择 .xlsx 文件（可多选，必须同类型）</el-button>
            <template #tip>
              <div style="color: #999; font-size: 12px; margin-top: 4px;">
                文件名前缀必须是 QUERY_/INSERT_/UPDATE_/DELETE_ 或 TEMPLATE_；混合类型将被拒绝
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button
            type="success"
            :loading="excelImporting"
            :disabled="!excelFileList.length"
            @click="doImportExcel"
          >开始导入</el-button>
          <el-button
            :loading="excelPreviewing"
            :disabled="!excelFileList.length"
            @click="doPreviewExcel"
          >预览（不落库）</el-button>
        </el-form-item>
      </el-form>

      <div v-if="excelPreview" style="margin-top: 12px;">
        <el-table :data="excelPreview.items" size="small">
          <el-table-column prop="fileName" label="文件" />
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="entityName" label="接口/模板名" />
          <el-table-column label="冲突" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.conflict" type="warning">冲突</el-tag>
              <el-tag v-else type="success" effect="plain">新增</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <ul v-if="excelPreview.errors && excelPreview.errors.length" style="color:#e6a23c; margin-top:8px;">
          <li v-for="(e, i) in excelPreview.errors" :key="i">{{ e }}</li>
        </ul>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import {
  exportConfig,
  importConfig,
  exportExcel,
  exportMarkdown,
  importExcel,
  previewExcelImport
} from '@/api/interfaceImportExport'
import { downloadBlob } from '@/utils/download'

// ─── FN-11 Task 5/6 · Excel/Markdown 导入导出 ───
const excelExportIds = ref('')
const excelExportType = ref('interface')
const excelExporting = ref(false)
const mdExporting = ref(false)
const excelStrategy = ref('SKIP')
const excelFileList = ref([])
const excelImporting = ref(false)
const excelPreviewing = ref(false)
const excelPreview = ref(null)
const excelUploadRef = ref(null)

function parseIds() {
  const list = (excelExportIds.value || '')
    .split(/[,，\s]+/)
    .map(s => Number(s.trim()))
    .filter(n => !Number.isNaN(n) && n > 0)
  if (!list.length) {
    ElMessage.warning('请输入至少一个有效 ID')
    return null
  }
  return list
}

async function doExportExcel() {
  const ids = parseIds()
  if (!ids) return
  excelExporting.value = true
  try {
    const blob = await exportExcel(ids, excelExportType.value)
    const name = ids.length === 1
      ? (excelExportType.value === 'template' ? 'TEMPLATE' : 'INTERFACE') + '_' + ids[0] + '.xlsx'
      : 'PowerGateway_excel.zip'
    downloadBlob(blob, name)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败：' + (e?.message || e))
  } finally {
    excelExporting.value = false
  }
}

async function doExportMarkdown() {
  const ids = parseIds()
  if (!ids) return
  mdExporting.value = true
  try {
    const blob = await exportMarkdown(ids, excelExportType.value)
    const name = ids.length === 1
      ? (excelExportType.value === 'template' ? 'TEMPLATE' : 'INTERFACE') + '_' + ids[0] + '.md'
      : 'PowerGateway_markdown.zip'
    downloadBlob(blob, name)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败：' + (e?.message || e))
  } finally {
    mdExporting.value = false
  }
}

function onExcelFileChange(file) {
  excelFileList.value = [...excelFileList.value, file]
  excelPreview.value = null
}
function onExcelFileRemove(file) {
  excelFileList.value = excelFileList.value.filter(f => f.uid !== file.uid)
  excelPreview.value = null
}

function collectRawFiles() {
  return excelFileList.value.map(f => f.raw).filter(Boolean)
}

async function doImportExcel() {
  const files = collectRawFiles()
  if (!files.length) return
  excelImporting.value = true
  try {
    const r = await importExcel(files, excelStrategy.value)
    ElMessage.success('导入完成：成功 ' + (r.imported || []).length
        + ' 条，跳过 ' + (r.skipped || []).length
        + ' 条，失败 ' + (r.failed || []).length + ' 条')
  } catch (e) {
    ElMessage.error('导入失败：' + (e?.message || e))
  } finally {
    excelImporting.value = false
  }
}

async function doPreviewExcel() {
  const files = collectRawFiles()
  if (!files.length) return
  excelPreviewing.value = true
  try {
    excelPreview.value = await previewExcelImport(files)
  } catch (e) {
    ElMessage.error('预览失败：' + (e?.message || e))
  } finally {
    excelPreviewing.value = false
  }
}

// ─── 导出 ──────────────────────────────────────────────────────────────────
const exporting = ref(false)

async function handleExport() {
  exporting.value = true
  try {
    const blob = await exportConfig()
    downloadBlob(blob, 'PowerGateway配置导出.zip')
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败，请重试')
  } finally {
    exporting.value = false
  }
}

// ─── 导入 ──────────────────────────────────────────────────────────────────
const strategy = ref('SKIP')
const selectedFile = ref(null)
const importing = ref(false)
const importResult = ref(null)
const uploadRef = ref(null)

function onFileChange(file) {
  selectedFile.value = file.raw
}

function onExceed() {
  ElMessage.warning('只能上传一个文件，请先移除当前文件')
}

async function handleImport() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 zip 文件')
    return
  }
  importing.value = true
  importResult.value = null
  try {
    const result = await importConfig(selectedFile.value, strategy.value)
    importResult.value = result
    if (strategy.value !== 'ASK') {
      const total = (result.imported || []).length + (result.failed || []).length
      ElMessage.success(`导入完成：成功 ${(result.imported || []).length} 条，跳过 ${(result.skipped || []).length} 条，失败 ${(result.failed || []).length} 条`)
    } else {
      ElMessage.info(`冲突检测完成：发现 ${(result.conflicts || []).length} 条冲突`)
    }
  } catch {
    ElMessage.error('导入失败，请检查文件格式是否正确')
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.import-export-page {
  padding: 16px;
}
.result-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  color: #555;
  max-height: 200px;
  overflow-y: auto;
}
.result-list li {
  margin-bottom: 4px;
}
</style>

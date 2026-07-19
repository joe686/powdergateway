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
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { exportConfig, importConfig } from '@/api/interfaceImportExport'
import { downloadBlob } from '@/utils/download'

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

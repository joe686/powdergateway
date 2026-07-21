<template>
  <el-card shadow="never" class="swagger-redirect">
    <el-empty description="Swagger 文档已在新窗口打开">
      <template #image>
        <el-icon style="font-size: 60px; color: var(--pg-primary);"><Link /></el-icon>
      </template>
      <div class="tips">
        <p>Swagger UI 由后端 SpringDoc 生成，与本平台视觉不完全统一，因此以外链方式打开。</p>
        <p>如果浏览器阻止了弹窗，可点击下方按钮再次打开。</p>
      </div>
      <el-button type="primary" @click="openSwagger">再次打开 Swagger UI</el-button>
    </el-empty>
  </el-card>
</template>

<script setup>
import { onMounted } from 'vue'
import { Link } from '@element-plus/icons-vue'

const SWAGGER_URL = '/swagger-ui/index.html'

function openSwagger() {
  window.open(SWAGGER_URL, '_blank', 'noopener')
}

onMounted(() => {
  openSwagger()
})
</script>

<style scoped>
.swagger-redirect { min-height: 60vh; }
.tips {
  color: var(--pg-text-secondary);
  font-size: 13px;
  line-height: 1.8;
  margin: 12px 0 20px;
  text-align: center;
}
</style>

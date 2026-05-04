<template>
  <el-card shadow="never">
    <el-empty :description="`「${title}」功能开发中，敬请期待`">
      <template #image>
        <el-icon style="font-size: 60px; color: #c0c4cc;"><Tools /></el-icon>
      </template>
    </el-empty>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta?.title || '该页面')
</script>

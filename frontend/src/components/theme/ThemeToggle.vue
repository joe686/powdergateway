<template>
  <div class="pg-theme-toggle" :title="'切换到' + (currentTheme === 'dark' ? '亮色' : '暗色')" @click="toggle">
    <div class="pg-theme-thumb">
      <span v-if="currentTheme === 'dark'">☾</span>
      <span v-else>☀</span>
    </div>
  </div>
</template>

<script setup>
import { useTheme } from '@/composables/useTheme'
const { currentTheme, toggle } = useTheme()
</script>

<style scoped>
.pg-theme-toggle {
  position: relative;
  width: 60px; height: 30px;
  border-radius: 999px;
  background: var(--pg-hover-surface);
  border: 1px solid var(--pg-glass-border);
  cursor: pointer;
  padding: 3px;
  transition: background 0.3s;
}
.pg-theme-toggle:hover { background: var(--pg-primary-soft); }
.pg-theme-thumb {
  width: 22px; height: 22px; border-radius: 50%;
  background: var(--pg-primary-grad);
  box-shadow: 0 2px 8px rgba(91, 124, 250, 0.4);
  display: grid; place-items: center;
  font-size: 12px; color: white;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
[data-theme="dark"] .pg-theme-thumb { transform: translateX(30px); }
</style>

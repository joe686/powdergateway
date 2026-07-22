<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <div class="login-logo-wrap">
          <el-icon class="login-logo"><Connection /></el-icon>
        </div>
        <h1 class="login-title">PowerGateway</h1>
        <p class="login-subtitle">可视化接口开发平台</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            size="large"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <p class="login-hint">默认账号：admin / Admin@123</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Connection } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { login, getMenuPermissions } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await login(form.username, form.password)
    userStore.setToken(res.token)
    userStore.setUserInfo(res.userInfo)
    try {
      const menus = await getMenuPermissions()
      userStore.setAllowedMenus(menus)
    } catch (e) {
      userStore.setAllowedMenus([])
    }
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    // 错误已由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* FB-038: 登录页毛玻璃重塑，对齐 UX-A 视觉基座
   背景透明让 App.vue 的 blob/stars 透出（不再用硬渐变遮盖） */
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--pg-bg-base);
  padding: 5vw;
  position: relative;
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 48px 40px;
  background: var(--pg-glass-bg-strong);
  border: 1px solid var(--pg-glass-border);
  border-radius: 20px;
  backdrop-filter: blur(var(--pg-glass-blur));
  -webkit-backdrop-filter: blur(var(--pg-glass-blur));
  box-shadow: var(--pg-glass-shadow);
  position: relative;
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo-wrap {
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--pg-primary-grad);
  box-shadow: 0 12px 28px rgba(91, 124, 250, 0.35);
}

.login-logo {
  font-size: 40px;
  color: #fff;
}

.login-title {
  font-size: 26px;
  font-weight: 700;
  margin: 4px 0 6px;
  background: var(--pg-primary-grad);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  letter-spacing: 0.5px;
}

.login-subtitle {
  font-size: 13px;
  color: var(--pg-text-secondary);
  letter-spacing: 0.3px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 12px;
  padding: 4px 12px;
  /* 用 --pg-track-bg：明色下略深于卡片、暗色下略亮于卡片，都是"输入控件轻微浮起"的暗示，
     避免暗色下比卡片更暗造成的"陷进去"突兀感 */
  background: var(--pg-track-bg);
  border: 1px solid var(--pg-line);
  box-shadow: none;
  transition: border-color 0.2s, background 0.2s;
}

.login-form :deep(.el-input__wrapper:hover) {
  border-color: var(--pg-line-strong);
}

.login-form :deep(.el-input__wrapper.is-focus) {
  background: var(--pg-glass-bg-strong);
  border-color: var(--pg-primary);
  box-shadow: 0 0 0 3px var(--pg-primary-soft);
}

.login-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 12px;
  background: var(--pg-primary-grad);
  border: none;
  box-shadow: 0 8px 20px rgba(91, 124, 250, 0.28);
  transition: transform 0.15s, box-shadow 0.15s;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(91, 124, 250, 0.38);
}

.login-btn:active {
  transform: translateY(0);
}

.login-hint {
  text-align: center;
  font-size: 12px;
  color: var(--pg-text-placeholder);
  margin-top: 8px;
}
</style>

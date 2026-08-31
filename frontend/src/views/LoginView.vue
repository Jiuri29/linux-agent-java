<template>
  <div class="login-wrap">
    <div class="blob b1" />
    <div class="blob b2" />
    <div class="blob b3" />
    <div class="grid-lines" />

    <el-card class="login-card" shadow="never">
      <div class="title">
        <div class="logo">
          <el-icon :size="22"><Cpu /></el-icon>
        </div>
        <div>
          <div class="t1">Linux AIOps Agent</div>
          <div class="t2">安全智能运维控制台</div>
        </div>
      </div>

      <div class="features">
        <span class="feature"><el-icon :size="12"><CircleCheck /></el-icon>实时监控</span>
        <span class="feature"><el-icon :size="12"><CircleCheck /></el-icon>受控终端</span>
        <span class="feature"><el-icon :size="12"><CircleCheck /></el-icon>审计追踪</span>
      </div>

      <el-alert
        v-if="isSetup"
        type="info"
        :closable="false"
        title="首次部署：尚未初始化管理员，请创建管理员账号"
        style="margin-bottom: 16px"
      />

      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" ref="userInputRef" placeholder="admin" size="large" data-testid="username">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            size="large"
            data-testid="password"
            @keyup.enter="submit"
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="isSetup" label="显示名称（可选）">
          <el-input v-model="form.displayName" size="large" />
        </el-form-item>
        <el-button type="primary" size="large" class="submit" :loading="loading" native-type="submit">
          {{ isSetup ? '初始化管理员并登录' : '登 录' }}
        </el-button>
        <el-button v-if="modeToggled !== undefined" text class="switch" @click="toggleMode">
          {{ isSetup ? '已有管理员？去登录' : '尚未初始化管理员？' }}
        </el-button>
      </el-form>

      <div class="foot">Linux AIOps Agent · Java Edition</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const serverInitialized = ref<boolean | null>(null)
const modeToggled = ref<boolean | undefined>(undefined)
const loading = ref(false)
const form = ref({ username: '', password: '', displayName: '' })
const userInputRef = ref()

const isSetup = computed(() => {
  if (modeToggled.value !== undefined) return modeToggled.value
  if (route.query.setup === '1') return true
  return serverInitialized.value === false
})

onMounted(async () => {
  const initialized = await auth.bootstrap()
  serverInitialized.value = initialized
  nextTick(() => userInputRef.value?.focus())
})

function toggleMode() {
  modeToggled.value = !isSetup.value
}

async function submit() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    if (isSetup.value) {
      await auth.initAdmin(form.value.username, form.value.password, form.value.displayName || undefined)
      ElMessage.success('管理员初始化完成')
    } else {
      await auth.login(form.value.username, form.value.password)
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect)
  } catch {
    /* 错误提示已由拦截器统一弹出 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(1000px 520px at 15% 0%, rgba(47, 107, 255, 0.1) 0%, transparent 55%),
    radial-gradient(900px 480px at 95% 90%, rgba(123, 63, 242, 0.08) 0%, transparent 55%),
    linear-gradient(180deg, #ffffff 0%, #f5f8ff 100%);
}
.grid-lines {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(var(--border) 1px, transparent 1px),
    linear-gradient(90deg, var(--border) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(700px 480px at 50% 42%, rgba(0, 0, 0, 0.5), transparent 100%);
  -webkit-mask-image: radial-gradient(700px 480px at 50% 42%, rgba(0, 0, 0, 0.5), transparent 100%);
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.18;
  pointer-events: none;
}
.b1 {
  width: 380px;
  height: 380px;
  background: var(--brand);
  top: -120px;
  left: -80px;
}
.b2 {
  width: 300px;
  height: 300px;
  background: #7b3ff2;
  bottom: -100px;
  right: -60px;
}
.b3 {
  width: 220px;
  height: 220px;
  background: #00b4d8;
  bottom: 10%;
  left: 12%;
  opacity: 0.12;
}
.login-card {
  width: 400px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid var(--border);
  box-shadow: 0 18px 50px rgba(47, 107, 255, 0.1);
  z-index: 1;
  animation: login-in 0.5s var(--ease-out) both;
}
@keyframes login-in {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.99);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.logo {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--brand), var(--brand-soft));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 6px 16px rgba(47, 107, 255, 0.3);
}
.t1 {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
}
.t2 {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
}
.features {
  display: flex;
  gap: 14px;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border);
}
.feature {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}
.feature .el-icon {
  color: var(--success);
}
.submit {
  width: 100%;
  margin-top: 4px;
  letter-spacing: 2px;
}
.switch {
  width: 100%;
  margin-top: 6px;
}
.foot {
  text-align: center;
  color: var(--text-secondary);
  font-size: 12px;
  margin-top: 18px;
}

/* 暗色模式下保留深色沉浸式登录背景 */
html.dark .login-wrap {
  background:
    radial-gradient(1200px 600px at 20% 0%, #1b2f55 0%, transparent 60%),
    radial-gradient(1000px 500px at 90% 100%, #14315c 0%, transparent 55%),
    linear-gradient(135deg, #0c1526 0%, #101d38 100%);
}
html.dark .blob {
  opacity: 0.5;
}
html.dark .b3 {
  opacity: 0.25;
}
html.dark .login-card {
  background: rgba(28, 35, 44, 0.92);
  backdrop-filter: blur(14px);
  border-color: rgba(255, 255, 255, 0.08);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.5);
}
</style>

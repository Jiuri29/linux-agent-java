<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <template #header>
        <div class="title">
          <el-icon :size="20"><Cpu /></el-icon>
          <span>Linux AIOps 安全运维 Agent</span>
        </div>
      </template>

      <el-alert
        v-if="isSetup"
        type="info"
        :closable="false"
        title="首次部署：尚未初始化管理员，请创建管理员账号"
        style="margin-bottom: 16px"
      />

      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="admin" data-testid="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            data-testid="password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item v-if="isSetup" label="显示名称（可选）">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" native-type="submit">
          {{ isSetup ? '初始化管理员并登录' : '登录' }}
        </el-button>
        <el-button v-if="modeToggled !== undefined" text style="width: 100%; margin-top: 4px" @click="toggleMode">
          {{ isSetup ? '已有管理员？去登录' : '尚未初始化管理员？' }}
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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

const isSetup = computed(() => {
  if (modeToggled.value !== undefined) return modeToggled.value
  if (route.query.setup === '1') return true
  return serverInitialized.value === false
})

onMounted(async () => {
  const initialized = await auth.bootstrap()
  serverInitialized.value = initialized
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
  background: linear-gradient(135deg, #102a43 0%, #243b55 100%);
}
.login-card {
  width: 380px;
}
.title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  justify-content: center;
}
</style>

<template>
  <div class="login-wrap">
    <div class="blob b1" />
    <div class="blob b2" />
    <div class="blob b3" />

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

      <el-alert
        v-if="isSetup"
        type="info"
        :closable="false"
        title="首次部署：尚未初始化管理员，请创建管理员账号"
        style="margin-bottom: 16px"
      />

      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="admin" size="large" data-testid="username">
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
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(1200px 600px at 20% 0%, #1b2f55 0%, transparent 60%),
    radial-gradient(1000px 500px at 90% 100%, #14315c 0%, transparent 55%),
    linear-gradient(135deg, #0c1526 0%, #101d38 100%);
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.5;
  pointer-events: none;
}
.b1 {
  width: 380px;
  height: 380px;
  background: #2f6bff;
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
  opacity: 0.25;
}
.login-card {
  width: 400px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(14px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 24px 60px rgba(4, 12, 30, 0.5);
  z-index: 1;
}
.title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 22px;
}
.logo {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2f6bff, #5b8cff);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 6px 16px rgba(47, 107, 255, 0.45);
}
.t1 {
  font-size: 17px;
  font-weight: 700;
  color: #1f2d3d;
}
.t2 {
  font-size: 12px;
  color: #7b8aa0;
  margin-top: 2px;
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
  color: #a3aec1;
  font-size: 12px;
  margin-top: 18px;
}
</style>

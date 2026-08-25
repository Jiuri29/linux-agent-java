<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <el-icon :size="22"><Cpu /></el-icon>
        <span>Linux AIOps Agent</span>
      </div>
      <el-menu
        router
        :default-active="$route.path"
        background-color="#001529"
        text-color="#a6adb4"
        active-text-color="#ffffff"
        class="menu"
      >
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon><span>总览</span>
        </el-menu-item>
        <el-menu-item index="/assets">
          <el-icon><Grid /></el-icon><span>资产中心</span>
        </el-menu-item>
        <el-menu-item index="/monitor">
          <el-icon><Warning /></el-icon><span>监控告警</span>
        </el-menu-item>
        <el-menu-item index="/services">
          <el-icon><Setting /></el-icon><span>服务中心</span>
        </el-menu-item>
        <el-menu-item index="/terminal">
          <el-icon><Monitor /></el-icon><span>受控终端</span>
        </el-menu-item>
        <el-menu-item index="/files">
          <el-icon><FolderOpened /></el-icon><span>文件管理</span>
        </el-menu-item>
        <el-menu-item index="/guardrails">
          <el-icon><Lock /></el-icon><span>安全护栏</span>
        </el-menu-item>
        <el-menu-item index="/chatops">
          <el-icon><ChatDotRound /></el-icon><span>ChatOps</span>
        </el-menu-item>
        <el-menu-item index="/mcp">
          <el-icon><Box /></el-icon><span>MCP 工具</span>
        </el-menu-item>
        <el-menu-item index="/agent-config">
          <el-icon><Connection /></el-icon><span>Agent API 配置</span>
        </el-menu-item>
        <el-menu-item index="/audit">
          <el-icon><Document /></el-icon><span>审计中心</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon><span>用户与角色</span>
        </el-menu-item>
        <el-menu-item index="/tests">
          <el-icon><Finished /></el-icon><span>验收测试</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header" height="52px">
        <div class="crumb">{{ $route.meta.title ?? '' }}</div>
        <el-dropdown @command="onCommand">
          <span class="user">
            <el-avatar :size="26" style="margin-right: 8px">{{ initial }}</el-avatar>
            {{ auth.displayName }}
            <el-tag v-if="auth.isAdmin" size="small" type="danger" effect="plain" style="margin-left: 6px">admin</el-tag>
            <el-icon style="margin-left: 4px"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const initial = computed(() => (auth.displayName || '?').slice(0, 1).toUpperCase())

async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    await auth.logout()
    router.push({ name: 'login' })
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: #001529;
  overflow-y: auto;
}
.brand {
  color: #fff;
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  font-size: 15px;
  padding: 16px;
}
.menu {
  border-right: none;
}
.header {
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.crumb {
  font-size: 15px;
  font-weight: 600;
}
.user {
  display: flex;
  align-items: center;
  cursor: pointer;
  outline: none;
}
.main {
  padding: 16px;
  overflow-y: auto;
}
</style>

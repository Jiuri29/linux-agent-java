<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '224px'" class="aside">
      <div class="brand" @click="$router.push('/')">
        <div class="logo">
          <el-icon :size="18"><Cpu /></el-icon>
        </div>
        <transition name="fade">
          <span v-if="!collapsed" class="brand-text">Linux AIOps</span>
        </transition>
      </div>

      <el-menu
        router
        :collapse="collapsed"
        :default-active="$route.path"
        background-color="transparent"
        text-color="#9aa8c0"
        active-text-color="#ffffff"
        class="menu"
      >
        <div v-if="!collapsed" class="menu-group">观测</div>
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon><template #title>总览</template>
        </el-menu-item>
        <el-menu-item index="/monitor">
          <el-icon><Warning /></el-icon><template #title>监控告警</template>
        </el-menu-item>
        <el-menu-item index="/assets">
          <el-icon><Grid /></el-icon><template #title>资产中心</template>
        </el-menu-item>
        <el-menu-item index="/services">
          <el-icon><Setting /></el-icon><template #title>服务中心</template>
        </el-menu-item>

        <div v-if="!collapsed" class="menu-group">操作</div>
        <el-menu-item index="/terminal">
          <el-icon><Monitor /></el-icon><template #title>受控终端</template>
        </el-menu-item>
        <el-menu-item index="/files">
          <el-icon><FolderOpened /></el-icon><template #title>文件管理</template>
        </el-menu-item>
        <el-menu-item index="/chatops">
          <el-icon><ChatDotRound /></el-icon><template #title>ChatOps</template>
        </el-menu-item>
        <el-menu-item index="/guardrails">
          <el-icon><Lock /></el-icon><template #title>安全护栏</template>
        </el-menu-item>

        <div v-if="!collapsed" class="menu-group">集成与治理</div>
        <el-menu-item index="/mcp">
          <el-icon><Box /></el-icon><template #title>MCP 工具</template>
        </el-menu-item>
        <el-menu-item index="/agent-config">
          <el-icon><Connection /></el-icon><template #title>Agent API</template>
        </el-menu-item>
        <el-menu-item index="/audit">
          <el-icon><Document /></el-icon><template #title>审计中心</template>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon><template #title>用户与角色</template>
        </el-menu-item>
        <el-menu-item index="/tests">
          <el-icon><Finished /></el-icon><template #title>验收测试</template>
        </el-menu-item>
      </el-menu>

      <div class="aside-footer">
        <el-button text class="collapse-btn" @click="collapsed = !collapsed">
          <el-icon :size="16">
            <component :is="collapsed ? 'Expand' : 'Fold'" />
          </el-icon>
        </el-button>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header" height="54px">
        <div class="crumb">
          <el-icon style="vertical-align: -2px; margin-right: 6px; color: var(--brand)">
            <component :is="sectionIcon" />
          </el-icon>
          {{ $route.meta.title ?? '' }}
        </div>
        <div class="header-actions">
          <el-tooltip content="主题切换" placement="bottom">
            <el-button circle text @click="toggle">
              <el-icon :size="16"><component :is="isDark ? 'Sunny' : 'Moon'" /></el-icon>
            </el-button>
          </el-tooltip>
          <el-dropdown @command="onCommand">
            <span class="user-chip">
              <el-avatar :size="28" class="avatar">{{ initial }}</el-avatar>
              <span class="uname">{{ auth.displayName }}</span>
              <el-tag v-if="auth.isAdmin" size="small" type="danger" effect="light" round>admin</el-tag>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component, route }">
          <transition name="fade-slide" mode="out-in">
            <div :key="route.fullPath" class="route-view">
              <component :is="Component" />
            </div>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'

const auth = useAuthStore()
const router = useRouter()
const { isDark, toggle } = useTheme()
const collapsed = ref(false)
const initial = computed(() => (auth.displayName || '?').slice(0, 1).toUpperCase())

const sectionIcon = computed(() => {
  const map: Record<string, string> = {
    dashboard: 'Odometer',
    monitor: 'Warning',
    assets: 'Grid',
    services: 'Setting',
    terminal: 'Monitor',
    files: 'FolderOpened',
    chatops: 'ChatDotRound',
    guardrails: 'Lock',
    mcp: 'Box',
    agentConfig: 'Connection',
    audit: 'Document',
    users: 'User',
    tests: 'Finished',
  }
  return map[(router.currentRoute.value.name as string) ?? 'dashboard'] ?? 'Odometer'
})

async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    try {
      await auth.logout()
    } catch {
      /* 会话已在本地清除，忽略远端失败 */
    } finally {
      router.push({ name: 'login' })
    }
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  transition: width 0.25s ease;
  overflow: hidden;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  cursor: pointer;
  min-height: 54px;
}
.logo {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--brand), var(--brand-light));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 4px 10px rgba(47, 107, 255, 0.4);
}
.brand-text {
  color: #fff;
  font-weight: 700;
  font-size: 15px;
  letter-spacing: 0.5px;
  white-space: nowrap;
}
.menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
.menu-group {
  color: #5a6a85;
  font-size: 11px;
  letter-spacing: 1.5px;
  padding: 14px 22px 4px;
  white-space: nowrap;
}
.aside-footer {
  padding: 8px 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
.collapse-btn {
  color: #9aa8c0;
  width: 100%;
}
.header {
  background: var(--el-bg-color);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
html.dark .header {
  border-bottom-color: rgba(255, 255, 255, 0.08);
}
.crumb {
  font-size: 15px;
  font-weight: 600;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
  padding: 4px 8px;
  border-radius: 20px;
  transition: background 0.2s;
}
.user-chip:hover {
  background: rgba(0, 0, 0, 0.04);
}
html.dark .user-chip:hover {
  background: rgba(255, 255, 255, 0.08);
}
.avatar {
  background: linear-gradient(135deg, var(--brand), var(--brand-light));
  font-weight: 600;
}
.uname {
  font-size: 13px;
}
.main {
  padding: 18px 20px;
  overflow-y: auto;
}
.route-view {
  width: 100%;
}
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>

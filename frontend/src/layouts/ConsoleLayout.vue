<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? 'var(--sidebar-collapsed)' : 'var(--sidebar-width)'" class="aside">
      <div class="brand" @click="$router.push('/')">
        <div class="logo">
          <el-icon :size="18"><Cpu /></el-icon>
        </div>
        <transition name="fade">
          <div v-if="!collapsed" class="brand-text">
            <div>Linux AIOps</div>
            <div class="brand-sub">智能运维控制台</div>
          </div>
        </transition>
      </div>

      <el-menu
        router
        :collapse="collapsed"
        :default-active="$route.path"
        background-color="transparent"
        text-color="var(--sidebar-text)"
        active-text-color="var(--menu-active-text)"
        class="menu"
      >
        <template v-for="group in menuGroups" :key="group.name">
          <div v-if="!collapsed" class="menu-group">{{ group.name }}</div>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="aside-footer">
        <div v-if="!collapsed" class="version">{{ appVersion }}</div>
        <el-button text class="collapse-btn" @click="collapsed = !collapsed">
          <el-icon :size="16">
            <component :is="collapsed ? 'Expand' : 'Fold'" />
          </el-icon>
        </el-button>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header" height="var(--header-height)">
        <div class="flex-row">
          <div class="crumb">
            <div class="crumb-icon">
              <el-icon :size="15"><component :is="sectionIcon" /></el-icon>
            </div>
            <div>
              <div class="crumb-title">{{ $route.meta.title ?? '' }}</div>
              <div class="crumb-desc">{{ $route.meta.desc ?? '' }}</div>
            </div>
          </div>
        </div>
        <div class="header-actions">
          <el-tooltip content="快速搜索 (Ctrl+K)" placement="bottom">
            <el-button text class="search-trigger" @click="openSearch">
              <el-icon :size="15"><Search /></el-icon>
              <span class="search-hint">搜索…</span>
              <kbd class="kbd">Ctrl K</kbd>
            </el-button>
          </el-tooltip>
          <el-tooltip content="活跃告警" placement="bottom">
            <el-badge
              :value="alertCount"
              :hidden="!alertCount"
              :max="99"
              class="bell-badge"
              :offset="[-2, 2]"
            >
              <el-button circle text @click="$router.push('/monitor')">
                <el-icon :size="16"><Bell /></el-icon>
              </el-button>
            </el-badge>
          </el-tooltip>
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

    <!-- 全局命令面板 -->
    <el-dialog
      v-model="searchOpen"
      class="palette-dialog"
      :show-close="false"
      :close-on-click-modal="true"
      width="560px"
      top="18vh"
      @opened="focusSearch"
    >
      <el-input
        v-model="searchQuery"
        ref="searchInputRef"
        size="large"
        placeholder="搜索页面或功能…"
        clearable
        @keydown="onPaletteKey"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div ref="paletteListRef" class="palette-list">
        <div
          v-for="item in filteredNav"
          :key="item.path"
          class="palette-item"
          :class="{ active: highlightIndex === item._i }"
          @mouseenter="highlightIndex = item._i"
          @click="goto(item.path)"
        >
          <div class="palette-icon"><el-icon :size="15"><component :is="item.icon" /></el-icon></div>
          <div class="palette-meta">
            <div class="palette-title">{{ item.title }}</div>
            <div class="palette-desc">{{ item.desc ?? item.path }}</div>
          </div>
          <el-icon class="palette-arrow" :size="13"><Right /></el-icon>
        </div>
        <div v-if="!filteredNav.length" class="palette-empty">无匹配结果</div>
      </div>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'

const auth = useAuthStore()
const router = useRouter()
const { isDark, toggle } = useTheme()
const collapsed = ref(false)
const appVersion = ref(`v1.0.0 · Java Edition`)
const alertCount = ref(0)

const initial = computed(() => (auth.displayName || '?').slice(0, 1).toUpperCase())

interface NavItem {
  icon: string
  title: string
  path: string
  desc?: string
}

const menuGroups: { name: string; items: NavItem[] }[] = [
  {
    name: '观测',
    items: [
      { icon: 'Odometer', title: '总览', path: '/' },
      { icon: 'Warning', title: '监控告警', path: '/monitor' },
      { icon: 'Grid', title: '资产中心', path: '/assets' },
      { icon: 'Setting', title: '服务中心', path: '/services' },
    ],
  },
  {
    name: '操作',
    items: [
      { icon: 'Monitor', title: '受控终端', path: '/terminal' },
      { icon: 'FolderOpened', title: '文件管理', path: '/files' },
      { icon: 'ChatDotRound', title: 'ChatOps', path: '/chatops' },
      { icon: 'Lock', title: '安全护栏', path: '/guardrails' },
    ],
  },
  {
    name: '集成与治理',
    items: [
      { icon: 'Box', title: 'MCP 工具', path: '/mcp' },
      { icon: 'Connection', title: 'Agent API', path: '/agent-config' },
      { icon: 'Document', title: '审计中心', path: '/audit' },
      { icon: 'User', title: '用户与角色', path: '/users' },
      { icon: 'Finished', title: '验收测试', path: '/tests' },
    ],
  },
]

const allNav = menuGroups.flatMap((g) => g.items)

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

/* ---------- 命令面板 ---------- */
const searchOpen = ref(false)
const searchQuery = ref('')
const searchInputRef = ref()
const paletteListRef = ref<HTMLElement>()
const highlightIndex = ref(0)

const filteredNav = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  const list = q
    ? allNav.filter((i) => i.title.toLowerCase().includes(q) || i.path.includes(q))
    : allNav
  return list.map((item, i) => ({ ...item, _i: i }))
})

function focusSearch() {
  nextTick(() => searchInputRef.value?.focus())
}

function openSearch() {
  searchQuery.value = ''
  highlightIndex.value = 0
  searchOpen.value = true
}

function moveHighlight(delta: number) {
  const len = filteredNav.value.length
  if (!len) return
  highlightIndex.value = (highlightIndex.value + delta + len) % len
  scrollToIndex(highlightIndex.value)
}

function scrollToIndex(i: number) {
  const el = paletteListRef.value?.children[i] as HTMLElement | undefined
  el?.scrollIntoView({ block: 'nearest' })
}

function onPaletteKey(e: KeyboardEvent) {
  if (!searchOpen.value) return
  const key = e.key
  if (key === 'ArrowDown') {
    e.preventDefault()
    moveHighlight(1)
  } else if (key === 'ArrowUp') {
    e.preventDefault()
    moveHighlight(-1)
  } else if (key === 'Enter') {
    const item = filteredNav.value[highlightIndex.value]
    if (item) {
      e.preventDefault()
      goto(item.path)
    }
  }
}

function goto(path: string) {
  searchOpen.value = false
  router.push(path)
}

watch(searchQuery, () => {
  highlightIndex.value = 0
  scrollToIndex(0)
})

function onGlobalKey(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    openSearch()
  } else if (e.key === 'Escape' && searchOpen.value) {
    searchOpen.value = false
  }
}

/* ---------- 通知角标 ---------- */
let alertTimer: number | undefined
async function refreshAlerts() {
  try {
    const list = (await api.monitor.alerts()) as Record<string, any>[]
    alertCount.value = list.filter((a) => ['active', 'acknowledged', 'escalated'].includes(a.status)).length
  } catch {
    /* ignore */
  }
}

onMounted(async () => {
  refreshAlerts()
  alertTimer = window.setInterval(refreshAlerts, 60_000)
  window.addEventListener('keydown', onGlobalKey)
  try {
    const ctx = (await api.platform.runtimeContext()) as Record<string, unknown>
    if (ctx.version) appVersion.value = `v${String(ctx.version)} · Java Edition`
  } catch {
    /* ignore */
  }
})

onBeforeUnmount(() => {
  if (alertTimer !== undefined) window.clearInterval(alertTimer)
  window.removeEventListener('keydown', onGlobalKey)
})
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: var(--sidebar-bg);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  transition: width var(--dur-base) var(--ease-out);
  overflow: hidden;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  min-height: var(--header-height);
}
.logo {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--brand-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(47, 107, 255, 0.32);
}
.brand-text {
  color: var(--text-primary);
  white-space: nowrap;
  line-height: 1.25;
}
.brand-text .brand-sub {
  font-size: 10.5px;
  font-weight: 500;
  color: var(--text-secondary);
  letter-spacing: 0.4px;
}
.brand-text div:first-child {
  font-weight: 700;
  font-size: 14.5px;
  letter-spacing: 0.3px;
}
.menu {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
.menu-group {
  color: var(--sidebar-text-dim);
  font-size: 10.5px;
  font-weight: 650;
  letter-spacing: 1.8px;
  padding: 16px 22px 4px;
  white-space: nowrap;
  text-transform: uppercase;
}
.menu :deep(.el-menu-item) {
  position: relative;
}
.menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: -10px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 3px;
  background: var(--menu-active-text);
}
.aside-footer {
  padding: 8px 12px;
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  gap: 8px;
}
.version {
  flex: 1;
  color: var(--text-secondary);
  font-size: 11px;
  font-family: var(--font-mono);
  padding-left: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.collapse-btn {
  color: var(--text-secondary);
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--dur-base) var(--ease-smooth);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.header {
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.flex-row {
  display: flex;
  align-items: center;
}
.crumb {
  display: flex;
  align-items: center;
  gap: 10px;
  overflow: hidden;
}
.crumb-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  color: var(--menu-active-text);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.crumb-title {
  font-size: var(--text-lg);
  font-weight: 650;
  color: var(--text-primary);
  line-height: 1.25;
  white-space: nowrap;
}
.crumb-desc {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  line-height: 1.35;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
.search-trigger {
  color: var(--text-secondary);
  border: 1px solid var(--border);
  border-radius: 8px;
  height: 30px;
  padding: 0 10px;
  gap: 6px;
}
.search-trigger:hover {
  color: var(--text-primary);
  border-color: var(--border-strong);
  background: var(--surface-sunken);
}
.search-hint {
  font-size: var(--text-sm);
}
.kbd {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-secondary);
  border: 1px solid var(--border-strong);
  border-radius: 4px;
  padding: 1px 4px;
  background: var(--surface-sunken);
}
.bell-badge :deep(.el-badge__content) {
  font-size: 10px;
  height: 15px;
  line-height: 15px;
  padding: 0 4px;
  min-width: 15px;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
  padding: 4px 8px;
  border-radius: 20px;
  transition: background var(--dur-fast) var(--ease-smooth);
}
.user-chip:hover {
  background: var(--surface-sunken);
}
.avatar {
  background: var(--brand-gradient);
  font-weight: 650;
}
.uname {
  font-size: var(--text-base);
  color: var(--text-primary);
}
.main {
  padding: 20px 22px;
  overflow-y: auto;
}
.route-view {
  width: 100%;
}

/* ---------- 命令面板 ---------- */
.palette-dialog :deep(.el-dialog__body) {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.palette-list {
  max-height: 380px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.palette-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-smooth);
}
.palette-item.active {
  background: var(--el-color-primary-light-9);
}
.palette-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--surface-sunken);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: inherit;
}
.palette-item.active .palette-icon {
  background: var(--el-color-primary-light-8);
  color: var(--brand);
}
.palette-meta {
  flex: 1;
  min-width: 0;
}
.palette-title {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--text-primary);
}
.palette-desc {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}
.palette-arrow {
  color: var(--text-secondary);
  opacity: 0;
  transition: opacity var(--dur-fast) ease;
}
.palette-item.active .palette-arrow {
  opacity: 1;
}
.palette-empty {
  text-align: center;
  color: var(--text-secondary);
  padding: 24px 0;
  font-size: var(--text-base);
}
</style>

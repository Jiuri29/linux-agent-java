import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layouts/ConsoleLayout.vue'),
    children: [
      { path: '', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '总览' } },
      { path: 'assets', name: 'assets', component: () => import('@/views/AssetsView.vue'), meta: { title: '资产中心' } },
      { path: 'monitor', name: 'monitor', component: () => import('@/views/MonitorView.vue'), meta: { title: '监控告警' } },
      { path: 'services', name: 'services', component: () => import('@/views/ServicesView.vue'), meta: { title: '服务中心' } },
      { path: 'terminal', name: 'terminal', component: () => import('@/views/TerminalView.vue'), meta: { title: '受控终端' } },
      { path: 'files', name: 'files', component: () => import('@/views/FilesView.vue'), meta: { title: '文件管理' } },
      { path: 'guardrails', name: 'guardrails', component: () => import('@/views/GuardrailsView.vue'), meta: { title: '安全护栏' } },
      { path: 'chatops', name: 'chatops', component: () => import('@/views/ChatopsView.vue'), meta: { title: 'ChatOps' } },
      { path: 'mcp', name: 'mcp', component: () => import('@/views/McpView.vue'), meta: { title: 'MCP 工具' } },
      { path: 'agent-config', name: 'agentConfig', component: () => import('@/views/AgentConfigView.vue'), meta: { title: 'Agent API 配置' } },
      { path: 'audit', name: 'audit', component: () => import('@/views/AuditView.vue'), meta: { title: '审计中心' } },
      { path: 'users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { title: '用户与角色' } },
      { path: 'tests', name: 'tests', component: () => import('@/views/TestsView.vue'), meta: { title: '验收测试' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  const initialized = await auth.bootstrap()
  if (!initialized) {
    return { name: 'login', query: { setup: '1' } }
  }
  if (!auth.isAuthenticated) {
    return { name: 'login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  return true
})

export default router

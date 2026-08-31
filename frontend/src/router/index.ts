import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layouts/ConsoleLayout.vue'),
    children: [
      { path: '', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '总览', desc: '平台实时资源、健康与统计摘要' } },
      { path: 'assets', name: 'assets', component: () => import('@/views/AssetsView.vue'), meta: { title: '资产中心', desc: '主机资产清单、分组、标签与快照' } },
      { path: 'monitor', name: 'monitor', component: () => import('@/views/MonitorView.vue'), meta: { title: '监控告警', desc: '采集快照、规则配置与告警处置' } },
      { path: 'services', name: 'services', component: () => import('@/views/ServicesView.vue'), meta: { title: '服务中心', desc: '应用服务生命周期与配置版本' } },
      { path: 'terminal', name: 'terminal', component: () => import('@/views/TerminalView.vue'), meta: { title: '受控终端', desc: '脚本模板执行与回放审计' } },
      { path: 'files', name: 'files', component: () => import('@/views/FilesView.vue'), meta: { title: '文件管理', desc: '服务器文件浏览、编辑与备份' } },
      { path: 'guardrails', name: 'guardrails', component: () => import('@/views/GuardrailsView.vue'), meta: { title: '安全护栏', desc: '高危命令拦截与规则管理' } },
      { path: 'chatops', name: 'chatops', component: () => import('@/views/ChatopsView.vue'), meta: { title: 'ChatOps', desc: '自然语言运维助手对话' } },
      { path: 'mcp', name: 'mcp', component: () => import('@/views/McpView.vue'), meta: { title: 'MCP 工具', desc: '工具注册、调用测试与授权' } },
      { path: 'agent-config', name: 'agentConfig', component: () => import('@/views/AgentConfigView.vue'), meta: { title: 'Agent API 配置', desc: 'Agent 接口地址与凭据配置' } },
      { path: 'audit', name: 'audit', component: () => import('@/views/AuditView.vue'), meta: { title: '审计中心', desc: '操作审计查询、导出与归档' } },
      { path: 'users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { title: '用户与角色', desc: '账号、角色与权限管理' } },
      { path: 'tests', name: 'tests', component: () => import('@/views/TestsView.vue'), meta: { title: '验收测试', desc: '端到端验收用例执行与结果' } },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'notFound',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true, title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

const APP_NAME = 'Linux AIOps'
router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · ${APP_NAME}` : APP_NAME
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  const initialized = await auth.bootstrap()
  if (initialized === false) {
    return { name: 'login', query: { setup: '1' } }
  }
  if (!auth.isAuthenticated) {
    return { name: 'login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  return true
})

export default router

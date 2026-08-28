import http from './http'

type Any = Record<string, any>

export const ops = {
  guardrails: {
    rules: (): Promise<Any[]> => http.get('/api/guardrails/rules'),
    saveRule: (body: Any): Promise<Any> => http.post('/api/guardrails/rules', body),
    setRuleStatus: (body: Any): Promise<Any> => http.post('/api/guardrails/rules/status', body),
    testRule: (body: Any): Promise<Any> => http.post('/api/guardrails/rules/test', body),
    exemptions: (): Promise<Any[]> => http.get('/api/guardrails/exemptions'),
    addExemption: (body: Any): Promise<Any> => http.post('/api/guardrails/exemptions', body),
    evaluate: (intent: string, commands: string[]): Promise<Any> =>
      http.post('/api/guardrails/evaluate', { intent, commands }),
    approvals: (limit = 50): Promise<Any[]> =>
      http.get('/api/guardrails/approvals', { params: { limit } }),
  },

  chatops: {
    templates: (): Promise<Any[]> => http.get('/api/chatops/templates'),
    message: (sessionId: string | null, message: string): Promise<Any> =>
      http.post('/api/chatops/message', { session_id: sessionId, message }, { silentError: true }),
    confirm: (sessionId: string, actionId: string): Promise<Any> =>
      http.post('/api/chatops/confirm', { session_id: sessionId, action_id: actionId }),
    session: (id: string): Promise<Any> => http.get('/api/chatops/session', { params: { id } }),
    demoCleanup: (): Promise<Any> => http.post('/api/chatops/demo-cleanup', {}),
  },

  mcp: {
    tools: (): Promise<Any[]> => http.get('/api/mcp/tools'),
    capabilities: (): Promise<Any> => http.get('/api/mcp/capabilities'),
    invoke: (name: string, params: Any = {}): Promise<Any> =>
      http.post('/api/mcp/invoke', { name, params }),
    rpc: (body: Any): Promise<Any> => http.post('/api/mcp/rpc', body),
    plugins: (): Promise<Any[]> => http.get('/api/mcp/plugins'),
    registerPlugin: (body: Any): Promise<Any> => http.post('/api/mcp/plugins/register', body),
    setPluginStatus: (body: Any): Promise<Any> => http.post('/api/mcp/plugins/status', body),
    setToolStatus: (body: Any): Promise<Any> => http.post('/api/mcp/tools/status', body),
    uninstallPlugin: (body: Any): Promise<Any> => http.post('/api/mcp/plugins/uninstall', body),
  },

  agentConfig: {
    get: (): Promise<Any> => http.get('/api/config/agent-api'),
    save: (body: Any): Promise<Any> => http.post('/api/config/agent-api', body),
    test: (): Promise<Any> => http.post('/api/config/agent-api/test', {}),
  },

  audit: {
    recent: (limit = 50): Promise<Any[]> => http.get('/api/audit/recent', { params: { limit } }),
    query: (params: Any): Promise<Any[]> => http.get('/api/audit/query', { params }),
    exportUrl: (params: Any) => {
      const qs = new URLSearchParams(
        Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''),
      ).toString()
      return '/api/audit/export' + (qs ? '?' + qs : '')
    },
    verify: (): Promise<Any> => http.get('/api/audit/verify'),
    archive: (days = 90): Promise<Any> => http.post('/api/audit/archive', { days }),
    forensics: (): Promise<Any> => http.get('/api/audit/forensics'),
    compliance: (days = 30): Promise<Any> =>
      http.get('/api/audit/reports/compliance', { params: { days } }),
    retention: (days = 90): Promise<Any> => http.get('/api/audit/retention', { params: { days } }),
  },

  tests: {
    catalog: (): Promise<Any> => http.get('/api/tests/catalog'),
    run: (ids: string[] = []): Promise<Any> => http.post('/api/tests/run', { ids }),
    runs: (limit = 20): Promise<Any[]> => http.get('/api/tests/runs', { params: { limit } }),
    runDetail: (id: string): Promise<Any> => http.get('/api/tests/runs/detail', { params: { id } }),
    reportUrl: (id: string) => `/api/tests/runs/report?id=${encodeURIComponent(id)}`,
  },
}

export default ops

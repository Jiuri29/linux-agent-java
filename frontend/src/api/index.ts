import http from './http'

type Any = Record<string, any>

export const api = {
  auth: {
    bootstrap: (): Promise<{ initialized: boolean }> => http.get('/api/auth/bootstrap'),
    initAdmin: (body: { username: string; password: string; displayName?: string }) =>
      http.post('/api/auth/init-admin', body),
    login: (username: string, password: string) =>
      http.post('/api/auth/login', { username, password }),
    logout: () => http.post('/api/auth/logout', {}),
  },

  platform: {
    runtimeContext: (): Promise<Any> => http.get('/api/runtime/context'),
    adminSummary: (): Promise<Any> => http.get('/api/admin/summary'),
    adminHealth: (): Promise<Any> => http.get('/api/admin/health'),
  },

  users: {
    list: (): Promise<Any[]> => http.get('/api/users'),
    create: (body: { username: string; password: string; display_name?: string; role?: string }) =>
      http.post('/api/users', body),
    update: (body: { username: string; display_name?: string; roles?: string[] }) =>
      http.post('/api/users/update', body),
    setStatus: (username: string, status: string) =>
      http.post('/api/users/status', { username, status }),
    resetPassword: (username: string, password: string) =>
      http.post('/api/users/reset-password', { username, password }),
    revokeSessions: (username: string): Promise<Any> =>
      http.post('/api/users/revoke-sessions', { username }),
    roles: (): Promise<Any[]> => http.get('/api/roles'),
    upsertRole: (name: string, permissions?: string[]) =>
      http.post('/api/roles/update', { name, permissions }),
    deleteRole: (name: string) => http.post('/api/roles/delete', { name }),
  },

  assets: {
    list: (params?: Any): Promise<Any> => http.get('/api/assets', { params }),
    detail: (id: string): Promise<Any> => http.get('/api/assets/detail', { params: { id } }),
    create: (body: Any): Promise<Any> => http.post('/api/assets', body),
    update: (body: Any): Promise<Any> => http.post('/api/assets/update', body),
    remove: (id: string) => http.post('/api/assets/delete', { id }),
    groups: (): Promise<string[]> => http.get('/api/assets/groups'),
    addGroup: (name: string) => http.post('/api/assets/groups', { name }),
    deleteGroup: (name: string) => http.post('/api/assets/groups/delete', { name }),
    tags: (): Promise<string[]> => http.get('/api/assets/tags'),
    addTag: (name: string) => http.post('/api/assets/tags', { name }),
    deleteTag: (name: string) => http.post('/api/assets/tags/delete', { name }),
    applications: (): Promise<string[]> => http.get('/api/assets/applications'),
    addApplication: (name: string) => http.post('/api/assets/applications', { name }),
    addPorts: (body: Any): Promise<Any> => http.post('/api/assets/services', body),
    dependencies: (): Promise<Any[]> => http.get('/api/assets/dependencies'),
    addDependency: (body: Any): Promise<Any> => http.post('/api/assets/dependencies', body),
    snapshots: (params?: Any): Promise<Any[]> => http.get('/api/assets/snapshots', { params }),
    sync: (source = 'manual'): Promise<Any> => http.post('/api/assets/sync', { source }),
  },

  monitor: {
    snapshot: (): Promise<Any> => http.get('/api/monitor/snapshot'),
    latest: (): Promise<Any> => http.get('/api/monitor/latest'),
    history: (params?: Any): Promise<Any[]> => http.get('/api/monitor/history', { params }),
    alertRules: (): Promise<Any[]> => http.get('/api/monitor/alert-rules'),
    saveAlertRules: (body: Any): Promise<Any> => http.post('/api/monitor/alert-rules', body),
    simulate: (body?: Any): Promise<Any[]> => http.post('/api/monitor/simulate', body ?? {}),
    alerts: (status?: string): Promise<Any[]> =>
      http.get('/api/monitor/alerts', { params: status ? { status } : undefined }),
    acknowledge: (id: string): Promise<Any> => http.post('/api/monitor/alerts/acknowledge', { id }),
    escalate: (id: string): Promise<Any> => http.post('/api/monitor/alerts/escalate', { id }),
    recover: (id: string): Promise<Any> => http.post('/api/monitor/alerts/recover', { id }),
    suppressions: (): Promise<Any[]> => http.get('/api/monitor/suppressions'),
    addSuppression: (body: Any): Promise<Any> => http.post('/api/monitor/suppressions', body),
    notifications: (): Promise<Any[]> => http.get('/api/monitor/notifications'),
    osSnapshot: (): Promise<Any> => http.get('/api/os/read-only-snapshot'),
    riskScan: (): Promise<Any> => http.get('/api/risk/scan'),
  },

  services: {
    list: (): Promise<Any> => http.get('/api/services'),
    upsert: (body: Any): Promise<Any> => http.post('/api/services', body),
    detail: (id: string): Promise<Any> => http.get('/api/services/detail', { params: { id } }),
    lifecycle: (body: Any): Promise<Any> => http.post('/api/services/lifecycle', body),
    readConfig: (serviceId: string): Promise<Any> =>
      http.get('/api/services/config/read', { params: { service_id: serviceId } }),
    versions: (serviceId: string): Promise<Any> =>
      http.get('/api/services/config/versions', { params: { service_id: serviceId } }),
    saveVersion: (body: Any): Promise<Any> => http.post('/api/services/config/versions', body),
    versionDetail: (id: string): Promise<Any> =>
      http.get('/api/services/config/versions/detail', { params: { id } }),
    diff: (from: string, to: string): Promise<Any> =>
      http.get('/api/services/config/diff', { params: { from, to } }),
    drift: (serviceId: string, versionId?: string): Promise<Any> =>
      http.get('/api/services/config/drift', { params: { service_id: serviceId, version_id: versionId } }),
    rollback: (body: Any): Promise<Any> => http.post('/api/services/config/rollback', body),
    changes: (params?: Any): Promise<Any> => http.get('/api/services/changes', { params }),
  },

  terminal: {
    templates: (): Promise<Any[]> => http.get('/api/terminal/templates'),
    saveTemplate: (body: Any): Promise<Any> => http.post('/api/terminal/templates', body),
    renderTemplate: (templateId: string, params: Any = {}): Promise<Any> =>
      http.post('/api/terminal/templates/render', { template_id: templateId, params }),
    run: (body: Any): Promise<Any> => http.post('/api/terminal/run', body),
    history: (limit = 50): Promise<Any[]> => http.get('/api/terminal/history', { params: { limit } }),
    replay: (id: string): Promise<Any> => http.get('/api/terminal/replay', { params: { id } }),
  },

  files: {
    list: (path?: string): Promise<Any> => http.get('/api/files/list', { params: path ? { path } : undefined }),
    read: (path: string): Promise<Any> => http.get('/api/files/read', { params: { path } }),
    write: (path: string, content: string): Promise<Any> =>
      http.post('/api/files/write', { path, content }),
    upload: (path: string, file: File): Promise<Any> => {
      const form = new FormData()
      form.append('file', file)
      form.append('path', path)
      return http.post('/api/files/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    },
    remove: (path: string): Promise<Any> => http.post('/api/files/delete', { path }),
    backups: (path?: string): Promise<Any> =>
      http.get('/api/files/backups', { params: path ? { path } : undefined }),
    diff: (path: string, backupId?: string): Promise<Any> =>
      http.get('/api/files/diff', { params: { path, backup_id: backupId } }),
    restore: (body: Any): Promise<Any> => http.post('/api/files/restore', body),
    prepareOperation: (body: Any): Promise<Any> => http.post('/api/files/operation/prepare', body),
  },
}

export default api

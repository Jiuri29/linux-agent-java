import { defineStore } from 'pinia'
import api from '@/api'
import { getCsrf, setCsrf } from '@/api/http'

export interface OpsUser {
  username: string
  display_name: string
  roles: string[]
}

const USER_KEY = 'ops_user'

function loadStoredUser(): OpsUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as OpsUser) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: loadStoredUser(),
    csrf: getCsrf(),
    initialized: undefined as boolean | undefined,
    checked: false,
  }),
  getters: {
    isAuthenticated: (s) => Boolean(s.user && s.csrf),
    displayName: (s) => s.user?.display_name || s.user?.username || '',
    roles: (s) => s.user?.roles ?? [],
    isAdmin(): boolean {
      return this.roles.includes('admin') || this.roles.includes('super_admin')
    },
  },
  actions: {
    persist(user: OpsUser, csrf: string) {
      this.user = user
      this.csrf = csrf
      setCsrf(csrf)
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    },
    clear() {
      this.user = null
      this.csrf = ''
      setCsrf('')
      localStorage.removeItem(USER_KEY)
    },
    async bootstrap(): Promise<boolean | null> {
      if (this.checked) {
        return this.initialized === true
      }
      try {
        const res = await api.auth.bootstrap()
        this.initialized = res.initialized
        this.checked = true
        return this.initialized === true
      } catch {
        return null
      }
    },
    async login(username: string, password: string) {
      const res = (await api.auth.login(username, password)) as unknown as Record<string, unknown>
      const user = res.user as unknown as OpsUser
      this.persist(
        {
          username: user.username,
          display_name: user.display_name,
          roles: user.roles ?? [],
        },
        String(res.csrf_token ?? ''),
      )
      this.checked = true
      this.initialized = true
    },
    async initAdmin(username: string, password: string, displayName?: string) {
      await api.auth.initAdmin({ username, password, displayName })
      await this.login(username, password)
    },
    async logout() {
      try {
        await api.auth.logout()
      } finally {
        this.clear()
      }
    },
  },
})

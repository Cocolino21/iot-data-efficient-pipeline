import { defineStore } from 'pinia'
import { api } from '@/services/api.js'

const LOGGED_OUT_FLAG = 'horizon.logged_out'

function isLoggedOutLocally() {
  try { return localStorage.getItem(LOGGED_OUT_FLAG) === '1' } catch { return false }
}
function setLoggedOutLocally(v) {
  try {
    if (v) localStorage.setItem(LOGGED_OUT_FLAG, '1')
    else localStorage.removeItem(LOGGED_OUT_FLAG)
  } catch { /* storage unavailable, ignore */ }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    loaded: false,
  }),
  getters: {
    isAuthenticated: (s) => !!s.user,
  },
  actions: {
    async load() {
      // FE-only "logout" flag: even if the BE cookie is still valid (HttpOnly
      // JSESSIONID etc.), refuse to treat the user as authenticated until they
      // explicitly sign in again (which calls beginLogin() to clear the flag).
      if (isLoggedOutLocally()) {
        this.user = null
        this.loaded = true
        return null
      }
      this.user = await api.me()
      this.loaded = true
      return this.user
    },
    async logout() {
      // Best-effort: tell the BE so the JWT cookie is actually invalidated.
      // Even if this fails, we proceed with the local "logged out" state.
      try { await api.logout() } catch (e) { console.warn('logout request failed', e) }
      // Wipe everything we can client-side. HttpOnly cookies (auth, JSESSIONID)
      // are untouchable from JS — the flag above is what enforces logout.
      clearAllReadableCookies()
      setLoggedOutLocally(true)
      this.user = null
      this.loaded = true
    },
    beginLogin() {
      // Call this right before redirecting the browser to /oauth2/authorization/google
      // so that when we land back on /dashboard the guard re-fetches /api/auth/me.
      setLoggedOutLocally(false)
      this.loaded = false
      this.user = null
    },
  },
})

function clearAllReadableCookies() {
  try {
    for (const c of document.cookie.split(';')) {
      const name = c.split('=')[0].trim()
      if (!name) continue
      // Wipe across common path variants. HttpOnly cookies will be silently
      // ignored by the browser — that's expected.
      document.cookie = `${name}=; Max-Age=0; path=/`
      document.cookie = `${name}=; Max-Age=0; path=/; domain=${location.hostname}`
    }
  } catch { /* ignore */ }
}

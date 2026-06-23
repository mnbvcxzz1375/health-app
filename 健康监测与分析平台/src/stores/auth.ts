import { defineStore } from 'pinia'
import { getAuthSession, loginAccount, logoutAccount, registerAccount } from '@/api/modules/auth'

type AuthUser = {
  id: string
  name: string
  email: string
  avatarUrl: string | null
}

type AuthSession = {
  token: string
  user: AuthUser
}

type RegisterPayload = {
  name: string
  email: string
  password: string
}

type LoginPayload = {
  email: string
  password: string
}

type UpdateProfilePayload = Partial<Pick<AuthUser, 'name' | 'email'>>

const SESSION_KEY = 'hm_auth_session'

function safeParse<T>(value: string | null, fallback: T): T {
  if (!value) return fallback
  try {
    return JSON.parse(value) as T
  } catch {
    return fallback
  }
}

function readSession(): AuthSession | null {
  if (typeof window === 'undefined') return null
  const session = safeParse<AuthSession | null>(window.localStorage.getItem(SESSION_KEY), null)
  if (!session?.token) return null
  if (session.token.startsWith('mock-token-')) return null
  return session
}

function writeSession(session: AuthSession | null) {
  if (typeof window === 'undefined') return
  if (!session) {
    window.localStorage.removeItem(SESSION_KEY)
    return
  }
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

const defaultAvatar =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160">' +
      '<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">' +
      '<stop offset="0%" stop-color="#34d399"/><stop offset="100%" stop-color="#22c55e"/></linearGradient></defs>' +
      '<rect width="160" height="160" rx="40" fill="url(#g)"/>' +
      '<circle cx="80" cy="62" r="28" fill="#ecfdf5"/>' +
      '<path d="M36 132c7-23 24-34 44-34s37 11 44 34" fill="#ecfdf5"/>' +
    '</svg>',
  )

const normalizeUser = (user: AuthUser | null): AuthUser | null => {
  if (!user) return null
  return {
    ...user,
    avatarUrl: user.avatarUrl || defaultAvatar,
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: '' as string,
    user: null as AuthUser | null,
    ready: false as boolean,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token && state.user),
    userName: (state) => state.user?.name ?? '未登录用户',
    avatarUrl: (state) => state.user?.avatarUrl ?? defaultAvatar,
  },
  actions: {
    hydrate() {
      const session = readSession()
      if (session) {
        this.token = session.token
        this.user = normalizeUser(session.user)
      }
      this.ready = true
    },
    async refreshSession() {
      try {
        const data = await getAuthSession()
        if (!data.user) throw new Error('未获取到用户信息')
        this.applySession({ token: this.token, user: data.user })
      } catch {
        this.clearSession()
      }
    },
    async register(payload: RegisterPayload) {
      const data = await registerAccount(payload)
      if (!data.token || !data.user) {
        throw new Error('注册失败')
      }
      this.applySession({ token: data.token, user: data.user })
    },
    async login(payload: LoginPayload) {
      const data = await loginAccount(payload)
      if (!data.token || !data.user) {
        throw new Error('登录失败')
      }
      this.applySession({ token: data.token, user: data.user })
    },
    async logout() {
      try {
        if (this.token) await logoutAccount()
      } finally {
        this.clearSession()
      }
    },
    updateAvatar(dataUrl: string) {
      if (!this.user) return
      this.user.avatarUrl = dataUrl
      this.persistUserPatch({ avatarUrl: dataUrl })
    },
    updateProfile(patch: UpdateProfilePayload) {
      if (!this.user) return
      const nextName = patch.name?.trim()
      const nextEmail = patch.email?.trim().toLowerCase()

      if (nextName) this.user.name = nextName
      if (nextEmail) this.user.email = nextEmail

      this.persistUserPatch({
        ...(nextName ? { name: nextName } : {}),
        ...(nextEmail ? { email: nextEmail } : {}),
      })
    },
    applySession(session: { token: string; user: AuthUser }) {
      this.token = session.token
      this.user = normalizeUser(session.user)
      if (this.user) {
        writeSession({ token: this.token, user: this.user })
      }
    },
    persistUserPatch(patch: Partial<AuthUser>) {
      if (!this.user) return
      this.user = { ...this.user, ...patch }
      if (this.user) {
        writeSession({ token: this.token, user: this.user })
      }
    },
    clearSession() {
      this.token = ''
      this.user = null
      writeSession(null)
    },
  },
})

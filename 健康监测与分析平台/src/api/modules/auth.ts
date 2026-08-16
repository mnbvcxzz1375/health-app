import { http } from '@/api/http'
import { cloneMock, getMockDb, withMockFallback } from '@/dev/mockApi'

export type AuthUser = {
  id: string
  name: string
  email: string
  avatarUrl: string | null
}

export type AuthResponse = {
  token: string
  user: AuthUser | null
}

export type AuthSessionResponse = {
  user: AuthUser | null
}

export async function registerAccount(payload: {
  name: string
  email: string
  password: string
}): Promise<AuthResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<AuthResponse>('/auth/register', payload)
      return data
    },
    () => {
      const mockUser: AuthUser = {
        id: 'mock-u-001',
        name: payload.name,
        email: payload.email,
        avatarUrl: null,
      }
      return { token: 'mock-token-' + Date.now(), user: mockUser }
    },
    true,
  )
}

export async function loginAccount(payload: { email: string; password: string }): Promise<AuthResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.post<AuthResponse>('/auth/login', payload)
      return data
    },
    () => {
      const db = getMockDb()
      const mockUser: AuthUser = {
        id: 'mock-u-001',
        name: db.profileSettings?.name ?? payload.email.split('@')[0],
        email: payload.email,
        avatarUrl: null,
      }
      return { token: 'mock-token-' + Date.now(), user: mockUser }
    },
    true,
  )
}

export async function getAuthSession(): Promise<AuthSessionResponse> {
  return withMockFallback(
    async () => {
      const { data } = await http.get<AuthSessionResponse>('/auth/me')
      return data
    },
    () => {
      const db = getMockDb()
      return {
        user: {
          id: 'mock-u-001',
          name: db.profileSettings?.name ?? 'Mock User',
          email: db.profileSettings?.email ?? 'mock@example.com',
          avatarUrl: null,
        },
      }
    },
  )
}

export async function logoutAccount(): Promise<void> {
  try {
    await http.post('/auth/logout')
  } catch {
    // mock mode: silent success
  }
}

import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse } from '../features/auth/types'

const ACCESS_TOKEN_KEY = 'ara_access_token'
const REFRESH_TOKEN_KEY = 'ara_refresh_token'

// AuthContext listens for this event to clear user state when a background
// token refresh fails (e.g. refresh token expired while the user was idle).
export const AUTH_LOGOUT_EVENT = 'auth:logout'

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export const http = axios.create({
  baseURL: '/api',
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

type RetryableConfig = InternalAxiosRequestConfig & { _retry?: boolean }

// A 401 from these endpoints is a genuine auth failure, not an expired token,
// so it must not trigger a refresh attempt (would mask a real "wrong password" error).
const SKIP_REFRESH_PATHS = ['/auth/login', '/auth/refresh', '/auth/register/candidate', '/auth/register/hr']

let refreshPromise: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('No refresh token available')
  }
  // Use plain axios (not the `http` instance) to avoid recursing into this interceptor.
  const response = await axios.post<AuthResponse>('/api/auth/refresh', { refreshToken })
  setTokens(response.data.accessToken, response.data.refreshToken)
  return response.data.accessToken
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetryableConfig | undefined

    const shouldSkipRefresh =
      !originalRequest ||
      error.response?.status !== 401 ||
      originalRequest._retry ||
      SKIP_REFRESH_PATHS.some((path) => originalRequest.url?.includes(path))

    if (shouldSkipRefresh) {
      return Promise.reject(error)
    }

    originalRequest._retry = true

    try {
      if (!refreshPromise) {
        refreshPromise = refreshAccessToken().finally(() => {
          refreshPromise = null
        })
      }
      const newAccessToken = await refreshPromise
      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
      return http(originalRequest)
    } catch (refreshError) {
      clearTokens()
      window.dispatchEvent(new Event(AUTH_LOGOUT_EVENT))
      return Promise.reject(refreshError)
    }
  },
)

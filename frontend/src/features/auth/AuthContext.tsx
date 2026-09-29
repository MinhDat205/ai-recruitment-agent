import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { AUTH_LOGOUT_EVENT, clearTokens, getAccessToken, setTokens } from '../../lib/http'
import { loginRequest, meRequest, registerCandidateRequest, registerHrRequest } from './api'
import type { LoginRequest, RegisterCandidateRequest, RegisterHrRequest, UserResponse } from './types'
import { AuthContext, type AuthContextValue } from './useAuth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<UserResponse | null>(null)
  const [isLoading, setIsLoading] = useState(() => Boolean(getAccessToken()))

  useEffect(() => {
    if (!getAccessToken()) {
      return
    }
    meRequest()
      .then(setUser)
      .catch(() => {
        clearTokens()
        setUser(null)
      })
      .finally(() => setIsLoading(false))
  }, [])

  useEffect(() => {
    // Phien tu het han (http.ts phat AUTH_LOGOUT_EVENT khi refresh token that bai) - xu ly giong
    // logout chu dong.
    function handleAuthLogout() {
      queryClient.clear()
      setUser(null)
    }
    window.addEventListener(AUTH_LOGOUT_EVENT, handleAuthLogout)
    return () => window.removeEventListener(AUTH_LOGOUT_EVENT, handleAuthLogout)
  }, [queryClient])

  // Cache TanStack Query khong gan voi tai khoan: khong xoa thi tai khoan dang nhap sau tren cung
  // tab se thay du lieu da cache cua tai khoan truoc (vd cong ty/dashboard cua HR khac) trong
  // staleTime. Xoa ca luc dang nhap lan dang xuat.
  const login = useCallback(
    async (payload: LoginRequest) => {
      const auth = await loginRequest(payload)
      setTokens(auth.accessToken, auth.refreshToken)
      const me = await meRequest()
      queryClient.clear()
      setUser(me)
      return me
    },
    [queryClient],
  )

  const registerCandidate = useCallback((payload: RegisterCandidateRequest) => {
    return registerCandidateRequest(payload)
  }, [])

  const registerHr = useCallback((payload: RegisterHrRequest) => {
    return registerHrRequest(payload)
  }, [])

  const logout = useCallback(() => {
    clearTokens()
    queryClient.clear()
    setUser(null)
  }, [queryClient])

  const value = useMemo<AuthContextValue>(
    () => ({ user, isLoading, login, registerCandidate, registerHr, logout }),
    [user, isLoading, login, registerCandidate, registerHr, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

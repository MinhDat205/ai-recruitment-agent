import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { AUTH_LOGOUT_EVENT, clearTokens, getAccessToken, setTokens } from '../../lib/http'
import { loginRequest, meRequest, registerCandidateRequest, registerHrRequest } from './api'
import type { LoginRequest, RegisterCandidateRequest, RegisterHrRequest, UserResponse } from './types'
import { AuthContext, type AuthContextValue } from './useAuth'

export function AuthProvider({ children }: { children: ReactNode }) {
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
    function handleAuthLogout() {
      setUser(null)
    }
    window.addEventListener(AUTH_LOGOUT_EVENT, handleAuthLogout)
    return () => window.removeEventListener(AUTH_LOGOUT_EVENT, handleAuthLogout)
  }, [])

  const login = useCallback(async (payload: LoginRequest) => {
    const auth = await loginRequest(payload)
    setTokens(auth.accessToken, auth.refreshToken)
    const me = await meRequest()
    setUser(me)
    return me
  }, [])

  const registerCandidate = useCallback((payload: RegisterCandidateRequest) => {
    return registerCandidateRequest(payload)
  }, [])

  const registerHr = useCallback((payload: RegisterHrRequest) => {
    return registerHrRequest(payload)
  }, [])

  const logout = useCallback(() => {
    clearTokens()
    setUser(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, isLoading, login, registerCandidate, registerHr, logout }),
    [user, isLoading, login, registerCandidate, registerHr, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

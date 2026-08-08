import { createContext, useContext } from 'react'
import type { LoginRequest, RegisterCandidateRequest, RegisterHrRequest, UserResponse } from './types'

export interface AuthContextValue {
  user: UserResponse | null
  isLoading: boolean
  login: (payload: LoginRequest) => Promise<UserResponse>
  registerCandidate: (payload: RegisterCandidateRequest) => Promise<UserResponse>
  registerHr: (payload: RegisterHrRequest) => Promise<UserResponse>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}

export type Role = 'HR' | 'CANDIDATE' | 'ADMIN'

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterCandidateRequest {
  email: string
  password: string
  fullName: string
  phone?: string
}

export interface RegisterHrRequest {
  email: string
  password: string
  fullName: string
  phone?: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInMs: number
}

export interface UserResponse {
  id: string
  email: string
  fullName: string
  role: Role
  phone: string | null
  avatarUrl: string | null
  isActive: boolean
  emailVerified: boolean
  createdAt: string
}

import { http } from '../../lib/http'
import type {
  AuthResponse,
  LoginRequest,
  RefreshRequest,
  RegisterCandidateRequest,
  RegisterHrRequest,
  UserResponse,
} from './types'

export async function loginRequest(payload: LoginRequest): Promise<AuthResponse> {
  const response = await http.post<AuthResponse>('/auth/login', payload)
  return response.data
}

export async function registerCandidateRequest(payload: RegisterCandidateRequest): Promise<UserResponse> {
  const response = await http.post<UserResponse>('/auth/register/candidate', payload)
  return response.data
}

export async function registerHrRequest(payload: RegisterHrRequest): Promise<UserResponse> {
  const response = await http.post<UserResponse>('/auth/register/hr', payload)
  return response.data
}

export async function refreshRequest(payload: RefreshRequest): Promise<AuthResponse> {
  const response = await http.post<AuthResponse>('/auth/refresh', payload)
  return response.data
}

export async function meRequest(): Promise<UserResponse> {
  const response = await http.get<UserResponse>('/auth/me')
  return response.data
}

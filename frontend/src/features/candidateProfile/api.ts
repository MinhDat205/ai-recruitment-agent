import { http } from '../../lib/http'
import type { CandidateProfileRequest, CandidateProfileResponse } from './types'

export async function getMyProfileRequest(): Promise<CandidateProfileResponse> {
  const response = await http.get<CandidateProfileResponse>('/candidates/profile/me')
  return response.data
}

export async function updateMyProfileRequest(
  payload: CandidateProfileRequest,
): Promise<CandidateProfileResponse> {
  const response = await http.put<CandidateProfileResponse>('/candidates/profile/me', payload)
  return response.data
}

import { http } from '../../lib/http'
import type { CandidateProfileRequest, CandidateProfileResponse, ResumeAutofillResponse } from './types'

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

// FR-U14 R-A1 - doc du lieu dien san tu CV chinh da DONE, khong goi AI. 409 NO_PRIMARY_RESUME_PARSED
// khi chua co CV chinh da phan tich xong (nut goi ham nay da bi khoa truoc o UI - xem R-A3).
export async function getAutofillFromResumeRequest(): Promise<ResumeAutofillResponse> {
  const response = await http.get<ResumeAutofillResponse>('/candidates/profile/me/autofill-from-resume')
  return response.data
}

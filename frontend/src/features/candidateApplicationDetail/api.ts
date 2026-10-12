import { http } from '../../lib/http'
import type { ApplicationCandidateDetail, TalentPoolConsent } from './types'

// FR-U08 E1 - chi goi endpoint phia UNG VIEN. Don cua nguoi khac/khong ton tai -> 404 (R-Q2).
export async function getCandidateApplicationDetailRequest(applicationId: string): Promise<ApplicationCandidateDetail> {
  const response = await http.get<ApplicationCandidateDetail>(`/candidates/applications/${applicationId}`)
  return response.data
}

// FR-U09 F4 - rut dong y luu ho so; 409 TALENT_POOL_CONSENT_NOT_ACTIVE khi don khong con dong y (R-K7).
export async function revokeTalentPoolConsentRequest(applicationId: string): Promise<TalentPoolConsent> {
  const response = await http.patch<TalentPoolConsent>(
    `/candidates/applications/${applicationId}/talent-pool-consent/revoke`,
  )
  return response.data
}

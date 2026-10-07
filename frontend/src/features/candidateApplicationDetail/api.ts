import { http } from '../../lib/http'
import type { ApplicationCandidateDetail } from './types'

// FR-U08 E1 - chi goi endpoint phia UNG VIEN. Don cua nguoi khac/khong ton tai -> 404 (R-Q2).
export async function getCandidateApplicationDetailRequest(applicationId: string): Promise<ApplicationCandidateDetail> {
  const response = await http.get<ApplicationCandidateDetail>(`/candidates/applications/${applicationId}`)
  return response.data
}

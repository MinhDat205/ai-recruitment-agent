import { http } from '../../lib/http'
import type { CandidateSearchPage, CandidateSearchParams, ScoringRunAuditItem } from './types'

// axios bo qua tham so undefined trong `params` - cac bo loc khong dat (jobId/status/minTotalScore...)
// khong bi gui len, khop dung hanh vi "khong loc" o tang backend (CAST(:param AS ...) IS NULL OR ...).
export async function searchCandidatesRequest(params: CandidateSearchParams): Promise<CandidateSearchPage> {
  const response = await http.get<CandidateSearchPage>('/hr/candidates', { params })
  return response.data
}

export async function listCriteriaNamesRequest(): Promise<string[]> {
  const response = await http.get<string[]>('/hr/candidates/criteria')
  return response.data
}

export async function getScoringRunAuditRequest(applicationId: string): Promise<ScoringRunAuditItem[]> {
  const response = await http.get<ScoringRunAuditItem[]>(`/hr/candidates/${applicationId}/audit/scoring-runs`)
  return response.data
}

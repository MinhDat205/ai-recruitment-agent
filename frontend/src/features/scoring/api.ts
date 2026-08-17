import { http } from '../../lib/http'
import type { ApplicationHrListItem, ScoringRun } from './types'

export async function listHrApplicationsRequest(jobId: string): Promise<ApplicationHrListItem[]> {
  const response = await http.get<ApplicationHrListItem[]>(`/hr/jobs/${jobId}/applications`)
  return response.data
}

export async function createScoringRunRequest(applicationId: string): Promise<ScoringRun> {
  const response = await http.post<ScoringRun>(`/hr/applications/${applicationId}/scoring-runs`)
  return response.data
}

export async function listScoringRunsRequest(applicationId: string): Promise<ScoringRun[]> {
  const response = await http.get<ScoringRun[]>(`/hr/applications/${applicationId}/scoring-runs`)
  return response.data
}

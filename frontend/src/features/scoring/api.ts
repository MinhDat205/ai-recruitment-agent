import { http } from '../../lib/http'
import type { ApplicationHrListItem, ApplicationSortOption, ScoringRun } from './types'

export async function listHrApplicationsRequest(
  jobId: string,
  sort: ApplicationSortOption,
): Promise<ApplicationHrListItem[]> {
  const response = await http.get<ApplicationHrListItem[]>(`/hr/jobs/${jobId}/applications`, {
    params: { sort },
  })
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

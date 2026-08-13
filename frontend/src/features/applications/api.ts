import { http } from '../../lib/http'
import type { Application, ApplicationCreateRequest, ApplicationHistoryEntry, ApplicationSummary } from './types'

export async function createApplicationRequest(payload: ApplicationCreateRequest): Promise<Application> {
  const response = await http.post<Application>('/candidates/applications', payload)
  return response.data
}

export async function getMyApplicationsRequest(): Promise<ApplicationSummary[]> {
  const response = await http.get<ApplicationSummary[]>('/candidates/applications')
  return response.data
}

export async function getApplicationHistoryRequest(id: string): Promise<ApplicationHistoryEntry[]> {
  const response = await http.get<ApplicationHistoryEntry[]>(`/candidates/applications/${id}/history`)
  return response.data
}

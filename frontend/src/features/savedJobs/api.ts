import { http } from '../../lib/http'
import type { SavedJob } from './types'

// FR-U16 E1-E4 (/api/candidates/saved-jobs). PUT/DELETE tra 204, khong body.
export async function saveJobRequest(jobId: string): Promise<void> {
  await http.put(`/candidates/saved-jobs/${jobId}`)
}

export async function unsaveJobRequest(jobId: string): Promise<void> {
  await http.delete(`/candidates/saved-jobs/${jobId}`)
}

export async function getSavedJobsRequest(): Promise<SavedJob[]> {
  const response = await http.get<SavedJob[]>('/candidates/saved-jobs')
  return response.data
}

export async function getSavedJobIdsRequest(): Promise<string[]> {
  const response = await http.get<string[]>('/candidates/saved-jobs/ids')
  return response.data
}

import { http } from '../../lib/http'
import type { JobDetail, JobSearchParams, JobSummary, PageResponse } from './types'

export async function searchJobsRequest(params: JobSearchParams): Promise<PageResponse<JobSummary>> {
  const response = await http.get<PageResponse<JobSummary>>('/public/jobs', { params })
  return response.data
}

export async function getJobDetailRequest(id: string): Promise<JobDetail> {
  const response = await http.get<JobDetail>(`/public/jobs/${id}`)
  return response.data
}

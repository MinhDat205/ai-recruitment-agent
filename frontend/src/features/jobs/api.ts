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

// Backend tra thang List<JobSummaryResponse>, KHONG boc PageResponse - cache goi y da gioi han
// san TOP_N=10 tu luc sinh o backend (JobRecommendationCacheService), khong can phan trang them.
export async function getJobRecommendationsRequest(): Promise<JobSummary[]> {
  const response = await http.get<JobSummary[]>('/candidates/job-recommendations')
  return response.data
}

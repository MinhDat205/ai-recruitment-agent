import { http } from '../../lib/http'
import type { JobDetail, JobRecommendationResponse, JobSearchParams, JobSummary, PageResponse } from './types'

// Axios serialize mang trong object params thanh "workMode[]=..." (da kiem bang buildURL thuc te),
// Spring @RequestParam List<String> workMode doi dung "workMode=A&workMode=B" (lap ten tham so,
// khong dau []) - phai tu dung URLSearchParams de kiem soat dung dinh dang. FR-U15 R-H3:
// categoryCode/locationCode ap dung CUNG ky thuat append nhu workMode (khong con result.set don gia
// tri) - cung ly do, cung dinh dang lap ten tham so.
function toSearchParams(params: JobSearchParams): URLSearchParams {
  const result = new URLSearchParams()
  if (params.keyword) result.set('keyword', params.keyword)
  for (const code of params.categoryCode ?? []) {
    result.append('categoryCode', code)
  }
  for (const code of params.locationCode ?? []) {
    result.append('locationCode', code)
  }
  if (params.salaryMin != null) result.set('salaryMin', String(params.salaryMin))
  if (params.salaryMax != null) result.set('salaryMax', String(params.salaryMax))
  if (params.hideUnlisted) result.set('hideUnlisted', 'true')
  for (const mode of params.workMode ?? []) {
    result.append('workMode', mode)
  }
  if (params.postedWithin) result.set('postedWithin', params.postedWithin)
  if (params.sort) result.set('sort', params.sort)
  if (params.page != null) result.set('page', String(params.page))
  if (params.size != null) result.set('size', String(params.size))
  return result
}

export async function searchJobsRequest(params: JobSearchParams): Promise<PageResponse<JobSummary>> {
  const response = await http.get<PageResponse<JobSummary>>('/public/jobs', {
    params: toSearchParams(params),
  })
  return response.data
}

export async function getJobDetailRequest(id: string): Promise<JobDetail> {
  const response = await http.get<JobDetail>(`/public/jobs/${id}`)
  return response.data
}

// FR-U15 R-S - backend tinh TRUC TIEP moi lan goi (khong con bo dem F1/FR-U04), tra
// {status, source, items} thay cho List<JobSummaryResponse> cu.
export async function getJobRecommendationsRequest(): Promise<JobRecommendationResponse> {
  const response = await http.get<JobRecommendationResponse>('/candidates/job-recommendations')
  return response.data
}

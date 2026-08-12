import { http } from '../../lib/http'
import type { PageResponse } from './types'
import type {
  HrJobListParams,
  InterviewTemplateOwnerRequest,
  InterviewTemplateOwnerResponse,
  JobCreateOwnerRequest,
  JobOwnerRequest,
  JobOwnerResponse,
  JobStatus,
} from './ownerTypes'

export async function listHrJobsRequest(params: HrJobListParams): Promise<PageResponse<JobOwnerResponse>> {
  const response = await http.get<PageResponse<JobOwnerResponse>>('/hr/jobs', { params })
  return response.data
}

export async function getHrJobRequest(id: string): Promise<JobOwnerResponse> {
  const response = await http.get<JobOwnerResponse>(`/hr/jobs/${id}`)
  return response.data
}

export async function createHrJobRequest(payload: JobCreateOwnerRequest): Promise<JobOwnerResponse> {
  const response = await http.post<JobOwnerResponse>('/hr/jobs', payload)
  return response.data
}

export async function updateHrJobRequest(id: string, payload: JobOwnerRequest): Promise<JobOwnerResponse> {
  const response = await http.put<JobOwnerResponse>(`/hr/jobs/${id}`, payload)
  return response.data
}

export async function changeHrJobStatusRequest(id: string, status: JobStatus): Promise<JobOwnerResponse> {
  const response = await http.patch<JobOwnerResponse>(`/hr/jobs/${id}/status`, { status })
  return response.data
}

// Xoa mem o backend (deleted_at) - response 204 khong co body.
export async function deleteHrJobRequest(id: string): Promise<void> {
  await http.delete(`/hr/jobs/${id}`)
}

export async function getInterviewTemplateRequest(jobId: string): Promise<InterviewTemplateOwnerResponse> {
  const response = await http.get<InterviewTemplateOwnerResponse>(`/hr/jobs/${jobId}/interview-template`)
  return response.data
}

export async function updateInterviewTemplateRequest(
  jobId: string,
  payload: InterviewTemplateOwnerRequest,
): Promise<InterviewTemplateOwnerResponse> {
  const response = await http.put<InterviewTemplateOwnerResponse>(`/hr/jobs/${jobId}/interview-template`, payload)
  return response.data
}

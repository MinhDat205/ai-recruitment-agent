import { http } from '../../lib/http'
import type { RubricCriterionRequest, RubricCriterionResponse, RubricResponse } from './ownerTypes'

export async function getRubricRequest(jobId: string): Promise<RubricResponse> {
  const response = await http.get<RubricResponse>(`/hr/jobs/${jobId}/rubric`)
  return response.data
}

export async function addRubricCriterionRequest(
  jobId: string,
  payload: RubricCriterionRequest,
): Promise<RubricCriterionResponse> {
  const response = await http.post<RubricCriterionResponse>(`/hr/jobs/${jobId}/rubric/criteria`, payload)
  return response.data
}

export async function updateRubricCriterionRequest(
  jobId: string,
  criterionId: string,
  payload: RubricCriterionRequest,
): Promise<RubricCriterionResponse> {
  const response = await http.put<RubricCriterionResponse>(
    `/hr/jobs/${jobId}/rubric/criteria/${criterionId}`,
    payload,
  )
  return response.data
}

// 409 RUBRIC_LOCKED o backend khi rubric.is_locked - response 204 khong co body khi thanh cong.
export async function deleteRubricCriterionRequest(jobId: string, criterionId: string): Promise<void> {
  await http.delete(`/hr/jobs/${jobId}/rubric/criteria/${criterionId}`)
}

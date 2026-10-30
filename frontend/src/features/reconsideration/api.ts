import { http } from '../../lib/http'
import type {
  CandidateReconsideration,
  HrReconsideration,
  HrReconsiderationFilter,
  HrReconsiderationList,
} from './types'

// FR-C09 H1 - yeu cau xem xet lai cua mot tin (moi chu ky). Quyen kiem o backend (404/403).
export async function getHrReconsiderationsRequest(
  jobId: string,
  filter: HrReconsiderationFilter,
): Promise<HrReconsiderationList> {
  const response = await http.get<HrReconsiderationList>(`/hr/jobs/${jobId}/reconsideration-requests`, {
    params: { status: filter },
  })
  return response.data
}

// FR-C09 H2 - giu quyet dinh tu choi. response null = HR khong nhap phan hoi. 409 RECONSIDERATION_NOT_OPEN khi yeu cau
// da quyet (tab khac) - noi goi hien cau backend.
export async function declineReconsiderationRequest(
  applicationId: string,
  response: string | null,
): Promise<HrReconsideration> {
  const result = await http.post<HrReconsideration>(
    `/hr/applications/${applicationId}/reconsideration-request/decline`,
    { response },
  )
  return result.data
}

// FR-C09 C1 - ung vien gui yeu cau xem xet lai. reason null = khong nhap (backend luu NULL). 409/400 tra cau co dinh
// (REQUIREMENT muc 4.3) - noi goi hien bang extractErrorMessage.
export async function requestReconsiderationRequest(
  applicationId: string,
  reason: string | null,
): Promise<CandidateReconsideration> {
  const response = await http.post<CandidateReconsideration>(
    `/candidates/applications/${applicationId}/reconsideration-request`,
    { reason },
  )
  return response.data
}

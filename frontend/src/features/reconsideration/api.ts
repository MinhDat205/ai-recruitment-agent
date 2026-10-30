import { http } from '../../lib/http'
import type { CandidateReconsideration } from './types'

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

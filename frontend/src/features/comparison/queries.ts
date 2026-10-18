import { useQuery, type QueryKey } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { applicationDetailInvalidateKeys } from '../applicationDetail/queries'
import { getApplicationComparisonRequest } from './api'

const COMPARISON_KEY_PREFIX = 'hr-application-comparison'

export function applicationComparisonKey(jobId: string, idsParam: string | null): QueryKey {
  return [COMPARISON_KEY_PREFIX, jobId, idsParam]
}

// Moi to hop ids cua mot Job. Export (FR-H16 L11) - B1 lam moi bang so sanh cua Job.
export function applicationComparisonKeyPrefix(jobId: string): QueryKey {
  return [COMPARISON_KEY_PREFIX, jobId]
}

// R-H3 - gui loi moi tu bang so sanh thanh cong thi lam moi: du lieu so sanh (C1, moi to hop ids cua Job nay) + cac
// khoa cua trang ho so don do (applicationDetailInvalidateKeys - da gom danh sach theo Job va /hr/candidates).
export function comparisonInvalidateKeys(jobId: string, applicationId: string): QueryKey[] {
  return [applicationComparisonKeyPrefix(jobId), ...applicationDetailInvalidateKeys(applicationId, jobId)]
}

// 4xx (400 lua chon sai, 403/404 quyen hoac Job khong ton tai, 409 chua cham xong) la ket qua chot, thu lai khong doi
// gi - chi thu lai 1 lan voi loi mang/5xx (cung cach applicationDetail/queries.ts).
function retryUnlessClientError(failureCount: number, error: unknown): boolean {
  if (isAxiosError(error)) {
    const status = error.response?.status
    if (status !== undefined && status >= 400 && status < 500) {
      return false
    }
  }
  return failureCount < 1
}

// KHONG tu tai lai dinh ky (R-R1): bang la anh tai thoi diem tai; HR bam "Tai lai" khi can.
export function useApplicationComparisonQuery(jobId: string, idsParam: string | null) {
  return useQuery({
    queryKey: applicationComparisonKey(jobId, idsParam),
    queryFn: () => getApplicationComparisonRequest(jobId, idsParam),
    retry: retryUnlessClientError,
  })
}

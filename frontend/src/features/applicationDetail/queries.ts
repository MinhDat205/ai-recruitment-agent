import { useQuery, type QueryKey } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { candidatesKeyPrefix } from '../candidates/queries'
import { hrApplicationsKeyPrefix, scoringRunsKey } from '../scoring/queries'
import {
  getApplicationExplanationRequest,
  getApplicationHrDetailRequest,
  getApplicationHrHistoryRequest,
  getApplicationParsedResumeRequest,
  getApplicationScoresRequest,
} from './api'

// Khoa query cua trang ho so don (FR-H09), tat ca theo applicationId.
const APPLICATION_DETAIL_KEY_PREFIX = 'hr-application-detail'

export function applicationDetailKey(applicationId: string) {
  return [APPLICATION_DETAIL_KEY_PREFIX, applicationId, 'detail']
}

function parsedResumeKey(applicationId: string) {
  return [APPLICATION_DETAIL_KEY_PREFIX, applicationId, 'parsed']
}

export function applicationScoresKey(applicationId: string) {
  return [APPLICATION_DETAIL_KEY_PREFIX, applicationId, 'scores']
}

export function applicationExplanationKey(applicationId: string) {
  return [APPLICATION_DETAIL_KEY_PREFIX, applicationId, 'explanation']
}

function historyKey(applicationId: string) {
  return [APPLICATION_DETAIL_KEY_PREFIX, applicationId, 'history']
}

// R-A5, R-S4 - MOI mutation tren trang (moi phong van, tu choi/trung tuyen, tao luot cham) lam moi du:
// dau trang (E1), diem (E3), giai thich (E4), lich su (E5), lot cham cua don, danh sach theo Job va
// /hr/candidates. Query chua mount (tab chua mo) chi bi danh dau stale, mo tab se tai lai.
export function applicationDetailInvalidateKeys(applicationId: string, jobId: string): QueryKey[] {
  return [
    applicationDetailKey(applicationId),
    applicationScoresKey(applicationId),
    applicationExplanationKey(applicationId),
    historyKey(applicationId),
    scoringRunsKey(applicationId),
    hrApplicationsKeyPrefix(jobId),
    candidatesKeyPrefix(),
  ]
}

// 4xx (400 id sai dinh dang, 403 don cong ty khac, 404 khong ton tai) la ket qua chot, thu lai khong doi
// gi - chi thu lai 1 lan voi loi mang/5xx (giong mac dinh retry: 1 cua queryClient).
function retryUnlessClientError(failureCount: number, error: unknown): boolean {
  if (isAxiosError(error)) {
    const status = error.response?.status
    if (status !== undefined && status >= 400 && status < 500) {
      return false
    }
  }
  return failureCount < 1
}

export function useApplicationHrDetailQuery(applicationId: string) {
  return useQuery({
    queryKey: applicationDetailKey(applicationId),
    queryFn: () => getApplicationHrDetailRequest(applicationId),
    retry: retryUnlessClientError,
  })
}

// enabled = CV DONE (R-T1..R-T3): CV dang cho/loi trich xuat thi khong goi E2.
export function useApplicationParsedResumeQuery(applicationId: string, enabled: boolean) {
  return useQuery({
    queryKey: parsedResumeKey(applicationId),
    queryFn: () => getApplicationParsedResumeRequest(applicationId),
    enabled,
    retry: retryUnlessClientError,
  })
}

export function useApplicationScoresQuery(applicationId: string) {
  return useQuery({
    queryKey: applicationScoresKey(applicationId),
    queryFn: () => getApplicationScoresRequest(applicationId),
    retry: retryUnlessClientError,
  })
}

// R-T14 - KHONG refetchInterval (khong tu poll khi PENDING). refetchOnMount 'always': mo lai tab Giai
// thich la tai lai E4, bat ke staleTime mac dinh 60s.
export function useApplicationExplanationQuery(applicationId: string) {
  return useQuery({
    queryKey: applicationExplanationKey(applicationId),
    queryFn: () => getApplicationExplanationRequest(applicationId),
    refetchOnMount: 'always',
    retry: retryUnlessClientError,
  })
}

// E5 phia HR - KHONG dung hook lich su phia ung vien (endpoint ung vien, HR bi 403, R-C3).
export function useApplicationHrHistoryQuery(applicationId: string) {
  return useQuery({
    queryKey: historyKey(applicationId),
    queryFn: () => getApplicationHrHistoryRequest(applicationId),
    retry: retryUnlessClientError,
  })
}

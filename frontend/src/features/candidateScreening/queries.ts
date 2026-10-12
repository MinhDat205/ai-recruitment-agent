import { useQuery } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { getMyApplicationScreeningRequest, getScreeningFormRequest } from './api'

export function screeningFormKey(jobId: string) {
  return ['candidate-screening-form', jobId]
}

export function myApplicationScreeningKey(applicationId: string) {
  return ['candidate-application-screening', applicationId]
}

// 4xx (404 tin khong con nhan don / don khong phai cua minh) la ket qua chot, thu lai khong doi gi - chi thu lai 1 lan
// voi loi mang/5xx (cung cach candidateApplicationDetail/queries.ts).
function retryUnlessClientError(failureCount: number, error: unknown): boolean {
  if (isAxiosError(error)) {
    const status = error.response?.status
    if (status !== undefined && status >= 400 && status < 500) {
      return false
    }
  }
  return failureCount < 1
}

// F1 - form nop don. refetch sau 409 SCREENING_QUESTIONS_CHANGED do JobApplyForm goi.
export function useScreeningFormQuery(jobId: string) {
  return useQuery({
    queryKey: screeningFormKey(jobId),
    queryFn: () => getScreeningFormRequest(jobId),
    retry: retryUnlessClientError,
  })
}

// F3 - ban chup bat bien (R-S4) nen khong can lam moi theo thao tac nao o trang chi tiet don.
export function useMyApplicationScreeningQuery(applicationId: string) {
  return useQuery({
    queryKey: myApplicationScreeningKey(applicationId),
    queryFn: () => getMyApplicationScreeningRequest(applicationId),
    retry: retryUnlessClientError,
  })
}

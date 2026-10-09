import { useQuery } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { getCandidateApplicationDetailRequest } from './api'

// Khoa query E1 cua trang chi tiet don (FR-U08). useWithdrawApplicationMutation lam moi khoa nay sau khi
// rut don (R-W3, R-W4).
export function candidateApplicationDetailKey(applicationId: string) {
  return ['candidate-application-detail', applicationId]
}

// 4xx (400 id sai dinh dang, 403, 404 don cua nguoi khac/khong ton tai) la ket qua chot, thu lai khong doi
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

export function useCandidateApplicationDetailQuery(applicationId: string) {
  return useQuery({
    queryKey: candidateApplicationDetailKey(applicationId),
    queryFn: () => getCandidateApplicationDetailRequest(applicationId),
    retry: retryUnlessClientError,
  })
}

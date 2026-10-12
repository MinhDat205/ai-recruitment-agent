import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { getCandidateApplicationDetailRequest, revokeTalentPoolConsentRequest } from './api'

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

// FR-U09 F4 - rut dong y. Thanh cong: lam moi E1 (khoi chuyen sang REVOKED tu du lieu backend, KHONG tu sua cache bang
// tay); tra ve promise de mutation cho toi khi E1 tai xong roi moi coi la xong. 409 TALENT_POOL_CONSENT_NOT_ACTIVE (tab
// khac vua rut): cung lam moi E1. Khong lam moi danh sach don hay lich su - trang thai don khong doi (R-K8).
export function useRevokeTalentPoolConsentMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: revokeTalentPoolConsentRequest,
    onSuccess: (_data, applicationId) =>
      queryClient.invalidateQueries({ queryKey: candidateApplicationDetailKey(applicationId) }),
    onError: (error, applicationId) => {
      if (isAxiosError(error) && error.response?.status === 409) {
        void queryClient.invalidateQueries({ queryKey: candidateApplicationDetailKey(applicationId) })
      }
    },
  })
}

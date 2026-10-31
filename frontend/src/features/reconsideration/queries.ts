import { useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { applicationDetailInvalidateKeys } from '../applicationDetail/queries'
import { MY_APPLICATIONS_QUERY_KEY } from '../applications/queries'
import { candidateApplicationDetailKey } from '../candidateApplicationDetail/queries'
import { declineReconsiderationRequest, getHrReconsiderationsRequest, requestReconsiderationRequest } from './api'
import { hrReconsiderationsKey } from './keys'
import type { HrReconsiderationFilter } from './types'

// FR-C09 C1 (UI.md 4c, muc 6). Thanh cong: lam moi E1 (khoi doi sang OPEN tu du lieu backend, KHONG tu sua cache bang tay)
// va danh sach don (chu "Đang chờ xem xét lại"); tra promise de mutation cho E1 tai xong. 409 (da gui o tab khac, het han,
// tin vua bi go...): lam moi E1 de khoi doi dung trang thai sau khi dong hop thoai.
export function useRequestReconsiderationMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ applicationId, reason }: { applicationId: string; reason: string | null }) =>
      requestReconsiderationRequest(applicationId, reason),
    onSuccess: (_data, { applicationId }) =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: candidateApplicationDetailKey(applicationId) }),
        queryClient.invalidateQueries({ queryKey: MY_APPLICATIONS_QUERY_KEY }),
      ]),
    onError: (error, { applicationId }) => {
      if (isAxiosError(error) && error.response?.status === 409) {
        void queryClient.invalidateQueries({ queryKey: candidateApplicationDetailKey(applicationId) })
        void queryClient.invalidateQueries({ queryKey: MY_APPLICATIONS_QUERY_KEY })
      }
    },
  })
}

// 4xx (403 tin cong ty khac, 404 tin/cong ty khong ton tai) la ket qua chot - chi thu lai 1 lan voi loi mang/5xx (UI.md 5c).
function retryUnlessClientError(failureCount: number, error: unknown): boolean {
  if (isAxiosError(error)) {
    const status = error.response?.status
    if (status !== undefined && status >= 400 && status < 500) {
      return false
    }
  }
  return failureCount < 1
}

// H1 - trang sua tin dung CUNG khoa (jobId, 'OPEN') cho so tren nhan tab va bo loc mac dinh cua tab (mot request).
export function useHrReconsiderationsQuery(jobId: string, filter: HrReconsiderationFilter) {
  return useQuery({
    queryKey: hrReconsiderationsKey(jobId, filter),
    queryFn: () => getHrReconsiderationsRequest(jobId, filter),
    retry: retryUnlessClientError,
  })
}

// Moi thao tac tren mot yeu cau (moi phong van qua InterviewInvitationDialog, giu tu choi) lam moi toan bo khoa cua trang
// ho so don - da gom H1 cua tin (moi bo loc), E1 co khoi yeu cau, danh sach tab "Ứng viên", /hr/candidates...
export function reconsiderationInvalidateKeys(applicationId: string, jobId: string): QueryKey[] {
  return applicationDetailInvalidateKeys(applicationId, jobId)
}

// H2 (UI.md 4g, muc 6). Thanh cong va 409 RECONSIDERATION_NOT_OPEN (tab khac vua quyet): lam moi H1 + E1 ho so don.
export function useDeclineReconsiderationMutation(jobId: string) {
  const queryClient = useQueryClient()
  const refresh = (applicationId: string) =>
    Promise.all(
      reconsiderationInvalidateKeys(applicationId, jobId).map((queryKey) => queryClient.invalidateQueries({ queryKey })),
    )
  return useMutation({
    mutationFn: ({ applicationId, response }: { applicationId: string; response: string | null }) =>
      declineReconsiderationRequest(applicationId, response),
    onSuccess: (_data, { applicationId }) => refresh(applicationId),
    onError: (error, { applicationId }) => {
      if (isAxiosError(error) && error.response?.status === 409) {
        void refresh(applicationId)
      }
    },
  })
}

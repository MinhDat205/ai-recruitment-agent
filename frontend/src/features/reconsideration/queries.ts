import { useMutation, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { MY_APPLICATIONS_QUERY_KEY } from '../applications/queries'
import { candidateApplicationDetailKey } from '../candidateApplicationDetail/queries'
import { requestReconsiderationRequest } from './api'

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

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { HR_JOBS_KEY, hrJobDetailKey } from '../jobs/ownerQueries'
import { hrApplicationsKeyPrefix } from '../scoring/queries'
import { updateBlindReviewRequest } from './api'

// FR-H16 B1 (UI.md 4a, 5a) - thanh cong: lam moi Job (blindReviewEnabled) va MOI danh sach don cua Job (nhan doi ngay khi
// HR chuyen tab). Dung lai khoa co san, khong khai lai.
export function useUpdateBlindReviewMutation(jobId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (enabled: boolean) => updateBlindReviewRequest(jobId, enabled),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: hrJobDetailKey(jobId) })
      queryClient.invalidateQueries({ queryKey: HR_JOBS_KEY })
      queryClient.invalidateQueries({ queryKey: hrApplicationsKeyPrefix(jobId) })
    },
  })
}

import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/useAuth'
import { getSavedJobIdsRequest, getSavedJobsRequest, saveJobRequest, unsaveJobRequest } from './api'

export const SAVED_JOB_IDS_KEY = ['saved-job-ids'] as const
export const SAVED_JOBS_KEY = ['saved-jobs'] as const

// Moi lan luu/bo luu cua MOT tin dung chung khoa nay - nut cua tin do o MOI the cung khoa trong luc cho (R-B5).
function toggleMutationKey(jobId: string) {
  return ['saved-job-toggle', jobId] as const
}

// R-B2/B5 - chi chay khi dang nhap vai tro CANDIDATE; khach va HR khong gui request nao toi /saved-jobs.
function useIsCandidate(): boolean {
  const { user } = useAuth()
  return user?.role === 'CANDIDATE'
}

// R-B4/B6 - MOT query cho ca trang: moi the va trang chi tiet doc cung khoa nay (cache TanStack Query).
export function useSavedJobIdsQuery() {
  const isCandidate = useIsCandidate()
  return useQuery({
    queryKey: SAVED_JOB_IDS_KEY,
    queryFn: getSavedJobIdsRequest,
    enabled: isCandidate,
  })
}

export function useSavedJobsQuery() {
  const isCandidate = useIsCandidate()
  return useQuery({
    queryKey: SAVED_JOBS_KEY,
    queryFn: getSavedJobsRequest,
    enabled: isCandidate,
  })
}

export function useIsTogglingSavedJob(jobId: string): boolean {
  return useIsMutating({ mutationKey: toggleMutationKey(jobId) }) > 0
}

interface ToggleContext {
  // Vi tri cua jobId trong danh sach ids TRUOC khi sua lac quan (-1 = chua luu) - de hoan lai dung ban cu cua tin nay
  // ma khong ghi de thay doi lac quan cua tin khac dang cho cung luc.
  previousIndex: number
}

// R-B5 (B6) - cap nhat lac quan: huy fetch ids dang chay, ghi lai vi tri cu, sua cache ngay; loi -> hoan lai dung vi
// tri cu cua tin nay; thanh cong -> KHONG refetch ids, chi danh dau cu ['saved-jobs'] (refetchType 'none').
function useToggleSavedJobMutation(jobId: string, mutationFn: (jobId: string) => Promise<void>, nextSaved: boolean) {
  const queryClient = useQueryClient()
  return useMutation<void, unknown, void, ToggleContext>({
    mutationKey: toggleMutationKey(jobId),
    mutationFn: () => mutationFn(jobId),
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: SAVED_JOB_IDS_KEY })
      const current = queryClient.getQueryData<string[]>(SAVED_JOB_IDS_KEY) ?? []
      const previousIndex = current.indexOf(jobId)
      queryClient.setQueryData<string[]>(SAVED_JOB_IDS_KEY, (old = []) => {
        const without = old.filter((id) => id !== jobId)
        // Luu moi -> len dau (backend sap saved_at DESC).
        return nextSaved ? [jobId, ...without] : without
      })
      return { previousIndex }
    },
    onError: (_error, _variables, context) => {
      if (!context) return
      queryClient.setQueryData<string[]>(SAVED_JOB_IDS_KEY, (old = []) => {
        const without = old.filter((id) => id !== jobId)
        if (context.previousIndex < 0) return without
        const restored = [...without]
        restored.splice(Math.min(context.previousIndex, restored.length), 0, jobId)
        return restored
      })
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: SAVED_JOBS_KEY, refetchType: 'none' })
    },
  })
}

export function useSaveJobMutation(jobId: string) {
  return useToggleSavedJobMutation(jobId, saveJobRequest, true)
}

export function useUnsaveJobMutation(jobId: string) {
  return useToggleSavedJobMutation(jobId, unsaveJobRequest, false)
}

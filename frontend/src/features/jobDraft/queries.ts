import { useMutation, useQueryClient } from '@tanstack/react-query'
import { generateJobDraftRequest, saveJobDraftRequest } from './api'
import type { JobAiDraftRequest, JobAiDraftSaveRequest } from './types'

// J1 - KHONG tu thu lai (moi lan goi ton mot luot ai-sync va loi goi AI); nguoi dung bam "Thu lai".
export function useGenerateJobDraftMutation() {
  return useMutation({
    mutationFn: (input: JobAiDraftRequest) => generateJobDraftRequest(input),
    retry: false,
  })
}

// J2 - tao tin moi: lam moi danh sach tin cua HR (cung khoa 'hr-jobs' voi features/jobs/ownerQueries).
export function useSaveJobDraftMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: JobAiDraftSaveRequest) => saveJobDraftRequest(input),
    retry: false,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['hr-jobs'] })
    },
  })
}

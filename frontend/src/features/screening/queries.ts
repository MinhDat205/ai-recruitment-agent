import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addScreeningQuestionRequest,
  deleteScreeningQuestionRequest,
  getScreeningQuestionsRequest,
  reorderScreeningQuestionsRequest,
  updateScreeningQuestionRequest,
} from './api'
import type { ScreeningQuestionRequest } from './types'

export function screeningQuestionsKey(jobId: string | undefined) {
  return ['hr-screening-questions', jobId]
}

export function useScreeningQuestionsQuery(jobId: string | undefined) {
  return useQuery({
    queryKey: screeningQuestionsKey(jobId),
    queryFn: () => getScreeningQuestionsRequest(jobId as string),
    enabled: Boolean(jobId),
  })
}

// Moi mutation S2-S5 lam moi S1 sau khi xong (UI.md 5a).
export function useAddScreeningQuestionMutation(jobId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: ScreeningQuestionRequest) => addScreeningQuestionRequest(jobId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: screeningQuestionsKey(jobId) }),
  })
}

export function useUpdateScreeningQuestionMutation(jobId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ questionId, payload }: { questionId: string; payload: ScreeningQuestionRequest }) =>
      updateScreeningQuestionRequest(jobId, questionId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: screeningQuestionsKey(jobId) }),
  })
}

export function useDeleteScreeningQuestionMutation(jobId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (questionId: string) => deleteScreeningQuestionRequest(jobId, questionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: screeningQuestionsKey(jobId) }),
  })
}

// KHONG doi thu tu lac quan (UI.md muc 6, 10): chi ghi danh sach backend tra ve sau khi S5 thanh cong. Loi bat ky
// (ke ca INVALID_SCREENING_ORDER - danh sach da doi o tab khac) -> tai lai S1.
export function useReorderScreeningQuestionsMutation(jobId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (questionIds: string[]) => reorderScreeningQuestionsRequest(jobId, questionIds),
    onSuccess: (data) => queryClient.setQueryData(screeningQuestionsKey(jobId), data),
    onError: () => queryClient.invalidateQueries({ queryKey: screeningQuestionsKey(jobId) }),
  })
}

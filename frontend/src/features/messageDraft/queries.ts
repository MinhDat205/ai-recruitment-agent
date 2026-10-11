import { useMutation, useQuery } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { createMessageDraftRequest, getDraftScenariosRequest } from './api'
import type { DraftSide, MessageDraftRequest } from './types'

const DRAFT_SCENARIOS_KEY_PREFIX = 'message-draft-scenarios'

export function isClientError(error: unknown): boolean {
  if (!isAxiosError(error)) {
    return false
  }
  const status = error.response?.status
  return status !== undefined && status >= 400 && status < 500
}

// Ma loi chuan hoa {"error": ...} cua backend, null khi khong co (loi mang, timeout).
export function errorCodeOf(error: unknown): string | null {
  if (!isAxiosError(error)) {
    return null
  }
  const data = error.response?.data as { error?: unknown } | undefined
  return typeof data?.error === 'string' ? data.error : null
}

// A1 - chi tai khi khoi mo (MessageDraftPanel chi render khi mo); khong tai lai dinh ky, khong tai lai khi quay lai
// cua so; 4xx la ket qua chot (khong thu lai), loi khac thu lai 1 lan (UI.md muc 5a).
export function useDraftScenariosQuery(side: DraftSide, applicationId: string) {
  return useQuery({
    queryKey: [DRAFT_SCENARIOS_KEY_PREFIX, side, applicationId],
    queryFn: () => getDraftScenariosRequest(side, applicationId),
    retry: (failureCount, error) => !isClientError(error) && failureCount < 1,
    refetchOnWindowFocus: false,
  })
}

// A2 - KHONG tu thu lai (moi lan goi ton mot luot cua nhom ai-sync va mot loi goi AI); nguoi dung bam "Thu lai".
export function useCreateMessageDraftMutation(side: DraftSide, applicationId: string) {
  return useMutation({
    mutationFn: (input: MessageDraftRequest) => createMessageDraftRequest(side, applicationId, input),
    retry: false,
  })
}

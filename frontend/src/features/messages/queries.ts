import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { useStallGuardedPolling } from '../../lib/useStallGuardedPolling'
import { NOTIFICATIONS_KEY_PREFIX } from '../notifications/queries'
import {
  getMessageThreadRequest,
  listConversationsRequest,
  markConversationReadRequest,
  sendMessageRequest,
} from './api'
import type { MessageSide } from './types'

// R-A1 - tab "Trao doi" tai lai M1 moi 10 giay. Khong bat refetchIntervalInBackground: TanStack Query mac dinh
// KHONG tai lai khi tab trinh duyet bi an.
const THREAD_POLL_INTERVAL_MS = 10_000

// R-A1 - hop thu dang mo tai lai M5 moi 30 giay.
const CONVERSATIONS_POLL_INTERVAL_MS = 30_000

// R-A2 - tu dung sau 20 phut lien tuc (cung nguong voi chuong thong bao).
const MAX_CONTINUOUS_POLL_MS = 20 * 60 * 1000

const MESSAGE_THREAD_KEY_PREFIX = 'message-thread'

// Khoa hop thu (M5) - gui tin / danh dau da doc lam moi moi trang hop thu qua tien to nay (R-A3, R-A4).
export const MESSAGE_CONVERSATIONS_KEY_PREFIX = 'message-conversations'

function conversationsKey(side: MessageSide, page: number) {
  return [MESSAGE_CONVERSATIONS_KEY_PREFIX, side, page]
}

export function messageThreadKey(side: MessageSide, applicationId: string) {
  return [MESSAGE_THREAD_KEY_PREFIX, side, applicationId]
}

// 4xx (400 id sai, 403, 404) la ket qua chot - khong thu lai, khong tai lai dinh ky (UI.md muc 6, R-G4).
export function isClientError(error: unknown): boolean {
  if (!isAxiosError(error)) {
    return false
  }
  const status = error.response?.status
  return status !== undefined && status >= 400 && status < 500
}

function retryUnlessClientError(failureCount: number, error: unknown): boolean {
  return !isClientError(error) && failureCount < 1
}

// M1 - tai lai 10 giay/lan, tu dung sau 20 phut (timedOut) hoac khi gap 4xx. resumePolling chay lai tu dau va
// tai ngay mot lan (nut "Tai lai").
export function useMessageThreadQuery(side: MessageSide, applicationId: string) {
  const { timedOut, resumePolling: resetStallTimer } = useStallGuardedPolling(MAX_CONTINUOUS_POLL_MS)

  const query = useQuery({
    queryKey: messageThreadKey(side, applicationId),
    queryFn: () => getMessageThreadRequest(side, applicationId),
    retry: retryUnlessClientError,
    refetchInterval: (current) =>
      timedOut || isClientError(current.state.error) ? false : THREAD_POLL_INTERVAL_MS,
  })

  return {
    ...query,
    timedOut,
    resumePolling: () => {
      resetStallTimer()
      query.refetch()
    },
  }
}

// M5 - hop thu: tai lai 30 giay/lan, khong tai lai khi tab trinh duyet an (mac dinh TanStack), tu dung sau 20 phut
// hoac khi gap 4xx. KHONG goi M3 (R-I4) - hop thu chi doc.
export function useConversationsQuery(side: MessageSide, page: number) {
  const { timedOut, resumePolling: resetStallTimer } = useStallGuardedPolling(MAX_CONTINUOUS_POLL_MS)

  const query = useQuery({
    queryKey: conversationsKey(side, page),
    queryFn: () => listConversationsRequest(side, page),
    retry: retryUnlessClientError,
    refetchInterval: (current) =>
      timedOut || isClientError(current.state.error) ? false : CONVERSATIONS_POLL_INTERVAL_MS,
  })

  return {
    ...query,
    timedOut,
    resumePolling: () => {
      resetStallTimer()
      query.refetch()
    },
  }
}

// M2 - R-A4: gui thanh cong thi lam moi M1 va M5 ngay, khong cho chu ky. Promise cua mutateAsync chi xong SAU
// khi M1 da tai lai, de noi goi cuon xuong cuoi luc tin moi da hien.
export function useSendMessageMutation(side: MessageSide, applicationId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { body: string; file: File | null }) => sendMessageRequest(side, applicationId, input),
    onSuccess: async () => {
      queryClient.invalidateQueries({ queryKey: [MESSAGE_CONVERSATIONS_KEY_PREFIX] })
      await queryClient.invalidateQueries({ queryKey: messageThreadKey(side, applicationId) })
    },
  })
}

// M3 - R-A3: danh dau da doc xong thi lam moi hop thu va chuong (backend da danh dau thong bao NEW_MESSAGE cua
// don nay - R-R3).
export function useMarkConversationReadMutation(side: MessageSide, applicationId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => markConversationReadRequest(side, applicationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [MESSAGE_CONVERSATIONS_KEY_PREFIX] })
      queryClient.invalidateQueries({ queryKey: [NOTIFICATIONS_KEY_PREFIX] })
    },
  })
}

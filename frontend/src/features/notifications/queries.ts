import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { listNotificationsRequest, markNotificationReadRequest } from './api'

const NOTIFICATIONS_KEY_PREFIX = 'notifications'

function notificationsKey(page: number, size: number) {
  return [NOTIFICATIONS_KEY_PREFIX, page, size]
}

// Chuong thong bao doc trang dau, so luong nho - vua du hien trong dropdown. unreadCount doc tu
// CHINH response nay (NotificationPageResponse backend boc chung page + unreadCount), khong co
// endpoint /unread-count rieng nen khong goi them request nao khac.
const BELL_PAGE_SIZE = 5
const LIST_PAGE_SIZE = 20
const POLL_INTERVAL_MS = 15000

// Khac scoring/resumes (dieu kien "con dang xu ly" ro rang tu status ban ghi, tu dung khi ban ghi
// xong): thong bao KHONG co trang thai "xong" tu nhien - luon co the co thong bao moi bat ky luc
// nao nguoi dung con dang nhap. Van ap dung dung nguyen tac stall-guard cua du an (khong polling vo
// han, mau useStallGuardedRefetch trong scoring/queries.ts va resumes/queries.ts): dung MOT nguong
// thoi gian lien tuc toi da, het nguong thi tu dung poll - nguoi dung mo lai chuong se tu "tai vu"
// (resumePolling), khong can F5 ca trang.
const MAX_CONTINUOUS_POLL_MS = 20 * 60 * 1000

function useStallGuardedPolling(timeoutMs: number) {
  const [timedOut, setTimedOut] = useState(false)
  const [resumeEpoch, setResumeEpoch] = useState(0)

  useEffect(() => {
    const timer = setTimeout(() => setTimedOut(true), timeoutMs)
    return () => {
      clearTimeout(timer)
      setTimedOut(false)
    }
  }, [timeoutMs, resumeEpoch])

  function resumePolling() {
    setTimedOut(false)
    setResumeEpoch((epoch) => epoch + 1)
  }

  return { timedOut, resumePolling }
}

// Dung cho chuong o CA HAI header (HrLayout/PublicHeader) va dropdown ben trong no.
export function useNotificationBellQuery() {
  const { timedOut, resumePolling: resetStallTimer } = useStallGuardedPolling(MAX_CONTINUOUS_POLL_MS)

  const query = useQuery({
    queryKey: notificationsKey(0, BELL_PAGE_SIZE),
    queryFn: () => listNotificationsRequest(0, BELL_PAGE_SIZE),
    refetchInterval: () => (timedOut ? false : POLL_INTERVAL_MS),
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

// Dung cho trang "xem tat ca" (ca HR lan candidate) - khong polling, chi tai khi mount/doi trang,
// giong PublicJobListPage/JobList.
export function useNotificationsListQuery(page: number) {
  return useQuery({
    queryKey: notificationsKey(page, LIST_PAGE_SIZE),
    queryFn: () => listNotificationsRequest(page, LIST_PAGE_SIZE),
  })
}

export function useMarkNotificationReadMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => markNotificationReadRequest(id),
    onSuccess: () => {
      // Khop CA hai bien the (chuong page=0/size=5 lan trang xem tat ca page=N/size=20) - prefix
      // rut gon, giong hrApplicationsKeyPrefix trong scoring/queries.ts.
      queryClient.invalidateQueries({ queryKey: [NOTIFICATIONS_KEY_PREFIX] })
    },
  })
}

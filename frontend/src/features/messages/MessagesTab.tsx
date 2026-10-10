import { useEffect, useRef, useState } from 'react'
import { AlertCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { MessageComposer } from './MessageComposer'
import { MessageList } from './MessageList'
import { isClientError, useMarkConversationReadMutation, useMessageThreadQuery } from './queries'
import type { MessageSide } from './types'

const READ_ONLY_NOTICE = 'Đơn đã rút, cuộc trao đổi chỉ còn xem.'

// Skeleton 3 bong bong (trai, phai, trai) - khong spinner toan trang, chua hien khung soan (UI.md muc 6).
function ThreadSkeleton() {
  return (
    <div
      className="flex flex-col gap-4 rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-3 sm:p-4"
      aria-hidden="true"
    >
      <div className="h-12 w-2/3 animate-pulse self-start rounded-m3-md bg-m3-surface-container sm:w-1/2" />
      <div className="h-12 w-2/3 animate-pulse self-end rounded-m3-md bg-m3-surface-container sm:w-1/2" />
      <div className="h-12 w-2/3 animate-pulse self-start rounded-m3-md bg-m3-surface-container sm:w-1/2" />
    </div>
  )
}

// Tab "Trao doi" (FR-C06, UI.md muc 4a, 4b, 6) - dung chung hai phia, chon API theo side (R-C2).
// Tu tai lai 10 giay/lan, dung khi tab trinh duyet an, tu dung sau 20 phut (R-A1, R-A2). Sau moi lan M1 tra
// unreadCount > 0 ma tai lieu dang hien thi thi goi M3 (R-A3) - GET M1 khong tu danh dau da doc (R-R2).
// onReadOnlyConflict: trang cha lam moi E1 (badge dau trang) khi gui gap 409 CONVERSATION_READ_ONLY.
export function MessagesTab({
  side,
  applicationId,
  onReadOnlyConflict,
}: {
  side: MessageSide
  applicationId: string
  onReadOnlyConflict: () => void
}) {
  const threadQuery = useMessageThreadQuery(side, applicationId)
  const markReadMutation = useMarkConversationReadMutation(side, applicationId)
  const [scrollToBottomSignal, setScrollToBottomSignal] = useState(0)
  const [readOnlyConflictMessage, setReadOnlyConflictMessage] = useState<string | null>(null)

  const unreadCount = threadQuery.data?.unreadCount ?? 0
  const dataUpdatedAt = threadQuery.dataUpdatedAt
  const { mutate: markRead } = markReadMutation
  // Moc du lieu M1 da xet gan nhat - moi lan M1 tra du lieu moi chi goi M3 TOI DA mot lan.
  const lastCheckedUpdateRef = useRef(0)

  // R-A3 - sau MOI lan M1 tra du lieu moi (dataUpdatedAt doi): unreadCount > 0 va tai lieu dang hien thi thi goi
  // M3. Tab trinh duyet an thi bo qua lan nay; lan tai lai ke tiep (sau khi hien lai) se xet lai.
  useEffect(() => {
    if (dataUpdatedAt === 0 || dataUpdatedAt === lastCheckedUpdateRef.current) {
      return
    }
    lastCheckedUpdateRef.current = dataUpdatedAt
    if (unreadCount > 0 && document.visibilityState === 'visible') {
      markRead()
    }
  }, [dataUpdatedAt, unreadCount, markRead])

  if (threadQuery.isLoading) {
    return <ThreadSkeleton />
  }

  const thread = threadQuery.data

  // Chi coi la loi khi CHUA co du lieu - lan tai lai ngam loi thi giu nguyen du lieu dang hien (UI.md muc 6).
  if (!thread) {
    if (isClientError(threadQuery.error)) {
      // R-G4 - 400/403/404 cung mot cau, khong phan biet; khong khung soan; tu tai lai da dung (queries.ts).
      return <p className="text-sm text-m3-on-surface">Không tìm thấy cuộc trao đổi.</p>
    }
    return (
      <div className="flex flex-col items-start gap-2">
        <p role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          Không tải được cuộc trao đổi.
        </p>
        <Button type="button" variant="outline" size="sm" onClick={() => threadQuery.refetch()}>
          Thử lại
        </Button>
      </div>
    )
  }

  const hasMessages = thread.messages.length > 0

  function handleReadOnlyConflict(message: string) {
    setReadOnlyConflictMessage(message)
    // Lam moi M1 (canSend = false -> khung soan bien mat) va E1 cua trang (badge dau trang).
    threadQuery.refetch()
    onReadOnlyConflict()
  }

  return (
    <div className="flex flex-col gap-4">
      {hasMessages ? (
        <MessageList
          messages={thread.messages}
          olderMessagesHidden={thread.olderMessagesHidden}
          side={side}
          applicationId={applicationId}
          scrollToBottomSignal={scrollToBottomSignal}
        />
      ) : thread.canSend ? (
        <div className="rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-6 text-center">
          <p className="text-sm text-m3-on-surface-variant">Chưa có tin nhắn nào. Hãy gửi tin đầu tiên ở bên dưới.</p>
        </div>
      ) : (
        <p className="text-sm text-m3-on-surface-variant">Chưa có tin nhắn nào.</p>
      )}

      {threadQuery.timedOut && (
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-sm text-m3-on-surface-variant">Đã tạm dừng tự cập nhật.</p>
          <Button type="button" variant="outline" size="sm" onClick={threadQuery.resumePolling}>
            Tải lại
          </Button>
        </div>
      )}

      {thread.canSend ? (
        <MessageComposer
          side={side}
          applicationId={applicationId}
          onSent={() => setScrollToBottomSignal((signal) => signal + 1)}
          onReadOnlyConflict={handleReadOnlyConflict}
        />
      ) : (
        <div className="flex flex-col gap-2">
          {readOnlyConflictMessage && (
            <p role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
              {readOnlyConflictMessage}
            </p>
          )}
          <p className="text-sm text-m3-on-surface-variant">{READ_ONLY_NOTICE}</p>
        </div>
      )}
    </div>
  )
}

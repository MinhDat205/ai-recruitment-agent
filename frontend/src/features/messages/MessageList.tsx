import { useLayoutEffect, useRef } from 'react'
import { MessageBubble } from './MessageBubble'
import type { Message, MessageSide } from './types'

// Cach day toi da (px) van coi la "dang o cuoi" - tin moi toi thi tu cuon xuong (UI.md muc 6).
const NEAR_BOTTOM_PX = 80

// Vung tin (UI.md muc 4a, 6, 9): khung vien m3-outline-variant, cao toi da 60vh, cuon doc BEN TRONG khung, tin cu
// tren / moi duoi. role="log" + aria-live="polite" de trinh doc man hinh doc tin moi; tabIndex 0 de cuon bang ban
// phim.
// Cuon: lan dau mo -> o cuoi; tin moi toi khi dang o cuoi (<= 80px) -> tu cuon; dang xem tin cu -> KHONG cuon,
// khong chen nut/nhan. scrollToBottomSignal tang (sau khi chinh minh gui thanh cong) -> cuon xuong cuoi.
export function MessageList({
  messages,
  olderMessagesHidden,
  side,
  applicationId,
  scrollToBottomSignal,
}: {
  messages: Message[]
  olderMessagesHidden: boolean
  side: MessageSide
  applicationId: string
  scrollToBottomSignal: number
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const nearBottomRef = useRef(true)
  const lastMessageId = messages.length > 0 ? messages[messages.length - 1].id : null

  function handleScroll() {
    const element = containerRef.current
    if (!element) {
      return
    }
    nearBottomRef.current = element.scrollHeight - element.scrollTop - element.clientHeight <= NEAR_BOTTOM_PX
  }

  // Tin moi nhat doi (lan dau render hoac tin moi toi): chi cuon khi nguoi dung dang o cuoi.
  useLayoutEffect(() => {
    const element = containerRef.current
    if (element && nearBottomRef.current) {
      element.scrollTop = element.scrollHeight
    }
  }, [lastMessageId])

  // Chinh minh vua gui xong: luon cuon xuong cuoi.
  useLayoutEffect(() => {
    const element = containerRef.current
    if (element && scrollToBottomSignal > 0) {
      element.scrollTop = element.scrollHeight
      nearBottomRef.current = true
    }
  }, [scrollToBottomSignal])

  return (
    <div
      ref={containerRef}
      onScroll={handleScroll}
      role="log"
      aria-live="polite"
      aria-label="Nội dung trao đổi"
      tabIndex={0}
      className="flex max-h-[60vh] flex-col gap-4 overflow-y-auto rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-3 sm:p-4"
    >
      {olderMessagesHidden && (
        <p className="text-center text-sm text-m3-on-surface-variant">Chỉ hiển thị 200 tin gần nhất.</p>
      )}
      {messages.map((message) => (
        <MessageBubble key={message.id} message={message} side={side} applicationId={applicationId} />
      ))}
    </div>
  )
}

import { useState } from 'react'
import { AlertCircle } from 'lucide-react'
import { formatDateTimeVi } from '../../lib/date'
import { AttachmentChip } from './AttachmentChip'
import { downloadMessageAttachmentRequest } from './api'
import type { Message, MessageSide } from './types'

// Nhan ben kia theo phia dang xem (UI.md muc 7). Phia ung vien KHONG thay ho ten HR (R-G2) - chi "Nha tuyen dung".
function counterpartLabel(side: MessageSide): string {
  return side === 'hr' ? 'Ứng viên' : 'Nhà tuyển dụng'
}

// Chep MAU ham tai CV cua trang ho so don (downloadApplicationResume.ts:14-24, thu muc tinh nang cham diem) - KHONG
// import (R-C2 cam thu muc nay import thu muc do): tai blob qua axios (can header Authorization) roi kich hoat
// <a download>.
async function saveAttachment(side: MessageSide, applicationId: string, message: Message): Promise<void> {
  const blob = await downloadMessageAttachmentRequest(side, applicationId, message.id)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = message.attachment?.fileName ?? 'tep-dinh-kem'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

// Mot tin (UI.md muc 4a, 5d, 9). Nhan nguoi gui + thoi diem la chu that, nam NGOAI bong bong tren nen trang;
// khong chi dua vao vi tri trai/phai hay mau de biet ai gui. Noi dung la loi cua nguoi (R-G1): van ban thuan,
// whitespace-pre-wrap giu xuong dong, KHONG render HTML, khong tu bien URL thanh lien ket. Khong hien "Da xem"
// (R-R5).
export function MessageBubble({
  message,
  side,
  applicationId,
}: {
  message: Message
  side: MessageSide
  applicationId: string
}) {
  const [downloadFailed, setDownloadFailed] = useState(false)
  const label = message.mine ? 'Bạn' : counterpartLabel(side)

  async function handleDownload() {
    setDownloadFailed(false)
    try {
      await saveAttachment(side, applicationId, message)
    } catch {
      setDownloadFailed(true)
    }
  }

  return (
    <article className={`flex flex-col gap-1 ${message.mine ? 'items-end' : 'items-start'}`}>
      <p
        className={`flex flex-col text-m3-label-md text-m3-on-surface-variant sm:flex-row sm:gap-1 ${
          message.mine ? 'items-end' : 'items-start'
        }`}
      >
        <span>{label}</span>
        <span className="hidden sm:inline" aria-hidden="true">
          ·
        </span>
        <time dateTime={message.createdAt}>{formatDateTimeVi(message.createdAt)}</time>
      </p>
      <div
        className={`flex max-w-[85%] flex-col gap-2 rounded-m3-md px-3 py-2 sm:max-w-[75%] ${
          message.mine ? 'bg-m3-primary-container text-m3-on-primary-container' : 'bg-m3-surface-container text-m3-on-surface'
        }`}
      >
        {message.body !== null && (
          <p className="text-m3-body-md break-words whitespace-pre-wrap">{message.body}</p>
        )}
        {message.attachment && (
          <AttachmentChip
            fileName={message.attachment.fileName}
            fileSize={message.attachment.fileSize}
            onDownload={handleDownload}
          />
        )}
      </div>
      {/* Cau loi tai tep NGOAI bong bong, tren nen the trang (UI.md muc 5d, 6) - khong can cap mau moi. */}
      {downloadFailed && (
        <p role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          Tải tệp thất bại, vui lòng thử lại.
        </p>
      )}
    </article>
  )
}

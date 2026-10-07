import { isAxiosError } from 'axios'
import { CalendarClock } from 'lucide-react'
import type { ReactNode } from 'react'
import { useInterviewInvitationQuery } from './queries'

// Gio Viet Nam, hien day-thang-nam + gio-phut - dung format voi formatAppliedAt/formatChangedAt
// (ApplicationHistoryTimeline.tsx) de nhat quan toan trang.
function formatScheduledAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Asia/Ho_Chi_Minh',
  })
}

interface InterviewInvitationDetailsProps {
  applicationId: string
  // Hien gi khi don chua co giay moi (404 INTERVIEW_INVITATION_NOT_FOUND). Mac dinh KHONG render gi (FR-U08
  // R-T5 - trang chi tiet don an ca khoi). Hop thoai "Xem giay moi" o trang danh sach truyen cau cu vao day
  // cho toi khi hop thoai do bi bo (FR-U08 R-P3).
  notFoundFallback?: ReactNode
}

// FR-U08 R-C2 - phan than giay moi phong van, tach nguyen van tu CandidateApplicationsPage
// (InterviewInvitationDetailDialog cu). Noi dung do HR soan, hien NGUYEN VAN (whitespace-pre-wrap giu xuong
// dong), khong render/tom tat lai gi them.
export function InterviewInvitationDetails({ applicationId, notFoundFallback = null }: InterviewInvitationDetailsProps) {
  const {
    data: invitation,
    isLoading,
    isError,
    error: invitationError,
  } = useInterviewInvitationQuery(applicationId, true)

  if (isLoading) {
    return <p className="text-sm text-m3-on-surface-variant">Đang tải giấy mời...</p>
  }

  if (isError) {
    // 404 INTERVIEW_INVITATION_NOT_FOUND: don co the bi REJECTED thang tu PENDING (khong qua
    // phong van) - khong phai loi, chi la khong co giay moi nao de xem. Loi khac (mang, 500...)
    // moi hien canh bao do.
    if (isAxiosError(invitationError) && invitationError.response?.status === 404) {
      return notFoundFallback
    }
    return <p className="text-sm text-m3-error">Không tải được giấy mời, vui lòng thử lại.</p>
  }

  if (!invitation) {
    return null
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-2 text-sm">
        <CalendarClock className="h-4 w-4 shrink-0 text-m3-primary" aria-hidden="true" />
        <span className="font-medium text-m3-on-surface">{formatScheduledAt(invitation.scheduledAt)}</span>
      </div>
      {invitation.location && (
        <p className="text-sm text-m3-on-surface-variant">
          <span className="font-medium text-m3-on-surface">Địa điểm: </span>
          {invitation.location}
        </p>
      )}
      <p className="text-sm font-medium text-m3-on-surface">{invitation.subject}</p>
      <p className="whitespace-pre-wrap text-sm text-m3-on-surface">{invitation.renderedContent}</p>
    </div>
  )
}

import { isAxiosError } from 'axios'
import { useState, type RefObject } from 'react'
import { formatDateTimeVi } from '../../lib/date'
import type { ApplicationStatus } from '../applications/types'
import { ConfirmInterviewSlotDialog } from './ConfirmInterviewSlotDialog'
import { InterviewSlotList } from './InterviewSlotList'
import { InterviewSlotPicker } from './InterviewSlotPicker'
import { INVITATION_TEXT } from './invitationLabels'
import { InvitationLetter } from './PreviousInvitationCard'
import { useInterviewInvitationQuery } from './queries'
import type { CandidateInterviewInvitationSlot } from './types'

// FR-U08 R-C2 - phan than giay moi phong van, tach nguyen van tu CandidateApplicationsPage
// (InterviewInvitationDetailDialog cu). Noi dung do HR soan, hien NGUYEN VAN (whitespace-pre-wrap giu xuong
// dong), khong render/tom tat lai gi them. Don chua co giay moi (404) -> KHONG render gi (R-T5); khoi bao
// ngoai (InvitationSection) tu an ca tieu de.
// FR-H12 UI.md 4d - CHI giay moi hieu luc (I4), gio Viet Nam (formatSlotTime), dau "Da chot" tren khung chot, "Dia diem:
// Chua co du lieu" khi thieu.
// FR-U10 UI.md muc 6 - chon hien thi theo trang thai don + I4: 4a (phan chon) khi INTERVIEW_INVITED, chua chot, con khung
// chua qua (R-U1); 4b-3 (cau "da qua", chi doc) khi INTERVIEW_INVITED, chua chot, moi khung da qua; 4b-1 (cau "Ban da
// chon...") khi INTERVIEW_INVITED va giay moi >= 2 khung da chot; con lai (giay moi 1 khung tu chot, don HIRED/REJECTED)
// chi doc, khong cau (4b-2, 4b-4, O1). "Da chon" suy tu slots.length >= 2, KHONG so confirmedAt voi sentAt.
export function InterviewInvitationDetails({
  applicationId,
  status,
  headingRef,
}: {
  applicationId: string
  status: ApplicationStatus
  // Tieu de khoi (InvitationSection) - nhan focus sau khi xac nhan thanh cong (L28).
  headingRef?: RefObject<HTMLHeadingElement | null>
}) {
  const {
    data: invitation,
    isLoading,
    isError,
    error: invitationError,
    dataUpdatedAt,
  } = useInterviewInvitationQuery(applicationId, true)
  // Khung dang cho xac nhan; null = hop xac nhan dong. Hop thoai nam o day (khong trong InterviewSlotPicker) vi phan chon
  // bi go ngay khi I4 doi sang da chot, con hop thoai phai song toi khi dong han (L30).
  const [pendingSlot, setPendingSlot] = useState<CandidateInterviewInvitationSlot | null>(null)

  if (isLoading) {
    return <p className="text-sm text-m3-on-surface-variant">Đang tải giấy mời...</p>
  }

  if (isError) {
    // 404 INTERVIEW_INVITATION_NOT_FOUND: don co the bi REJECTED thang tu PENDING (khong qua
    // phong van) - khong phai loi, chi la khong co giay moi nao de xem. Loi khac (mang, 500...)
    // moi hien canh bao do.
    if (isAxiosError(invitationError) && invitationError.response?.status === 404) {
      return null
    }
    return <p className="text-sm text-m3-error">Không tải được giấy mời, vui lòng thử lại.</p>
  }

  if (!invitation) {
    return null
  }

  const confirmedSlotId = invitation.slots.find((slot) => slot.confirmed)?.id ?? null
  const invited = status === 'INTERVIEW_INVITED'
  const unconfirmed = invitation.confirmedAt === null
  // Gio may luc I4 tai xong lan gan nhat (dataUpdatedAt) - KHONG goi Date.now() trong render (react-hooks/purity, L31).
  // I4 tai lai khi quay lai tab va sau moi 409/404 cua C1; backend van la chot chan (R-C6, 409 INTERVIEW_SLOT_PASSED).
  const nowMs = dataUpdatedAt
  const hasOpenSlot = invitation.slots.some((slot) => new Date(slot.scheduledAt).getTime() > nowMs)
  const canPick = invited && unconfirmed && hasOpenSlot

  let notice: string | null = null
  if (canPick) {
    notice = INVITATION_TEXT.candidateUnconfirmed
  } else if (invited && unconfirmed) {
    notice = INVITATION_TEXT.candidateAllPassed
  } else if (invited && invitation.confirmedAt !== null && invitation.slots.length >= 2) {
    notice = INVITATION_TEXT.candidateConfirmedAt(formatDateTimeVi(invitation.confirmedAt))
  }

  return (
    <div className="flex w-full min-w-0 flex-col gap-3">
      {notice && <p className="text-sm break-words text-m3-on-surface">{notice}</p>}
      {canPick ? (
        <InterviewSlotPicker slots={invitation.slots} nowMs={nowMs} onRequestConfirm={setPendingSlot} />
      ) : (
        <InterviewSlotList slots={invitation.slots} confirmedSlotId={confirmedSlotId} variant="candidate" />
      )}
      <InvitationLetter subject={invitation.subject} content={invitation.renderedContent} />
      <ConfirmInterviewSlotDialog
        applicationId={applicationId}
        slot={pendingSlot}
        onClose={() => setPendingSlot(null)}
        onCloseAutoFocus={(event, confirmed) => {
          // Thanh cong (L28), hoac nut mo hop thoai da bi go (vd 409 ALREADY_CONFIRMED lam I4 doi sang da chot roi nguoi
          // dung bam "Huỷ"): Radix khong con cho tra focus - dat focus vao tieu de khoi. Con lai de Radix tra focus.
          if (confirmed || !canPick) {
            event.preventDefault()
            headingRef?.current?.focus()
          }
        }}
      />
    </div>
  )
}

import { isAxiosError } from 'axios'
import { InterviewSlotList } from './InterviewSlotList'
import { INVITATION_TEXT } from './invitationLabels'
import { InvitationLetter } from './PreviousInvitationCard'
import { useInterviewInvitationQuery } from './queries'

// FR-U08 R-C2 - phan than giay moi phong van, tach nguyen van tu CandidateApplicationsPage
// (InterviewInvitationDetailDialog cu). Noi dung do HR soan, hien NGUYEN VAN (whitespace-pre-wrap giu xuong
// dong), khong render/tom tat lai gi them. Don chua co giay moi (404) -> KHONG render gi (R-T5); khoi bao
// ngoai (InvitationSection) tu an ca tieu de.
// FR-H12 UI.md 4d - CHI giay moi hieu luc (I4): danh sach khung CHI DOC (gio Viet Nam, formatSlotTime), dau "Da chot" tren
// khung chot, "Dia diem: Chua co du lieu" khi thieu. Cau huong dan (Q8) chi khi confirmedAt null. KHONG nut/o chon
// khung - phan chon la cua FR-U10.
export function InterviewInvitationDetails({ applicationId }: { applicationId: string }) {
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
      return null
    }
    return <p className="text-sm text-m3-error">Không tải được giấy mời, vui lòng thử lại.</p>
  }

  if (!invitation) {
    return null
  }

  const confirmedSlotId = invitation.slots.find((slot) => slot.confirmed)?.id ?? null

  return (
    <div className="flex min-w-0 flex-col gap-3">
      {invitation.confirmedAt === null && (
        <p className="text-sm break-words text-m3-on-surface">{INVITATION_TEXT.candidateUnconfirmed}</p>
      )}
      <InterviewSlotList slots={invitation.slots} confirmedSlotId={confirmedSlotId} variant="candidate" />
      <InvitationLetter subject={invitation.subject} content={invitation.renderedContent} />
    </div>
  )
}

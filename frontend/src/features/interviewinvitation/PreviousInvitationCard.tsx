import { formatDateTimeVi } from '../../lib/date'
import { InterviewSlotList } from './InterviewSlotList'
import { INVITATION_TEXT } from './invitationLabels'
import type { InterviewInvitationResponse } from './types'

// FR-H12 UI.md 4c-2 - mot giay moi CU, hien NGUYEN VAN (khung, khung chot, tieu de, noi dung). Khong nut thao tac nao,
// khong lam mo (opacity) - giu tuong phan (UI.md muc 10). supersededAt = sentAt cua giay moi da thay no (R-G3 buoc 4).
export function PreviousInvitationCard({ invitation }: { invitation: InterviewInvitationResponse }) {
  return (
    <article className="flex min-w-0 flex-col gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-4">
      <p className="text-sm break-words text-m3-on-surface-variant">
        {invitation.supersededAt
          ? INVITATION_TEXT.previousMeta(formatDateTimeVi(invitation.sentAt), formatDateTimeVi(invitation.supersededAt))
          : INVITATION_TEXT.sentAt(formatDateTimeVi(invitation.sentAt))}
      </p>
      <InterviewSlotList slots={invitation.slots} confirmedSlotId={invitation.confirmedSlotId} variant="hr" />
      <InvitationLetter subject={invitation.subject} content={invitation.renderedContent} />
    </article>
  )
}

// Thu moi NGUYEN VAN HR da gui - day du, giu xuong dong (whitespace-pre-wrap), van ban thuong (khong render HTML).
export function InvitationLetter({ subject, content }: { subject: string; content: string }) {
  return (
    <div className="flex min-w-0 flex-col gap-1">
      <p className="text-sm font-medium break-words text-m3-on-surface">{subject}</p>
      <p className="text-sm whitespace-pre-wrap break-words text-m3-on-surface">{content}</p>
    </div>
  )
}

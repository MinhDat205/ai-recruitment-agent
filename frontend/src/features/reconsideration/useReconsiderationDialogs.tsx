import { useState } from 'react'
import { InterviewInvitationDialog } from '../interviewinvitation/InterviewInvitationDialog'
import { DeclineReconsiderationDialog } from './DeclineReconsiderationDialog'
import { reconsiderationInvalidateKeys } from './queries'
import type { ReconsiderationTarget } from './types'

// Hai hop thoai cua ReconsiderationCard, dat o NOI GOI (tab 4e, khoi H09 4f) chu khong trong the: bo loc "Đang chờ" lam the
// bien mat ngay khi yeu cau vua quyet, hop thoai trong the se bi go truoc khi kip dong va tra focus. File rieng vi
// react-refresh chi cho file component export component.
// "Mời phỏng vấn" mo DUNG InterviewInvitationDialog cua FR-H12 (khong dung hop thu hai, R-D1), lam moi H1 + moi khoa cua ho
// so don. onDeclined: noi goi dat focus + thong bao aria-live (UI.md 4g, muc 9).
export function useReconsiderationDialogs(jobId: string, onDeclined: () => void) {
  const [inviteTarget, setInviteTarget] = useState<ReconsiderationTarget | null>(null)
  const [declineTarget, setDeclineTarget] = useState<ReconsiderationTarget | null>(null)

  const dialogs = (
    <>
      <InterviewInvitationDialog
        application={inviteTarget ? { id: inviteTarget.applicationId, candidateName: inviteTarget.candidateName } : null}
        jobId={jobId}
        onOpenChange={(open) => !open && setInviteTarget(null)}
        invalidateQueryKeys={inviteTarget ? reconsiderationInvalidateKeys(inviteTarget.applicationId, jobId) : []}
      />
      <DeclineReconsiderationDialog
        target={declineTarget}
        jobId={jobId}
        onClose={() => setDeclineTarget(null)}
        onDeclined={onDeclined}
      />
    </>
  )

  return { openInvite: setInviteTarget, openDecline: setDeclineTarget, dialogs }
}

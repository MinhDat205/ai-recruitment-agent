import { useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  ApplicationStatusConfirmDialog,
  type ApplicationStatusConfirmTarget,
} from '../applications/ApplicationStatusConfirmDialog'
import type { ApplicationStatus } from '../applications/types'
import { InterviewInvitationDialog } from '../interviewinvitation/InterviewInvitationDialog'
import { applicationDetailInvalidateKeys } from './queries'
import type { ApplicationHrDetail } from './types'

// R-A1 - nut hien theo DUNG may trang thai FR-H07 (ApplicationStatusService.ALLOWED_TRANSITIONS, phia
// UI la nextActionsFor cua ApplicationsTab): PENDING -> Moi phong van, Tu choi; INTERVIEW_INVITED ->
// Trung tuyen, Tu choi; con lai khong co nut. CHI phu thuoc status (R-A2): khong khoa/an vi don chua
// cham, dang cham, cham loi hay CV chua trich xuat. Backend van la chot chan that.
function nextActionsFor(status: ApplicationStatus): { canInvite: boolean; canReject: boolean; canHire: boolean } {
  if (status === 'PENDING') {
    return { canInvite: true, canReject: true, canHire: false }
  }
  if (status === 'INTERVIEW_INVITED') {
    return { canInvite: false, canReject: true, canHire: true }
  }
  return { canInvite: false, canReject: false, canHire: false }
}

// Thanh thao tac FR-H07 o dau trang - nam TRONG luong trang (khong fixed/sticky, UI.md muc 8). Ba nut
// cung variant="outline", cung muc nhan manh, khong mau do/xanh (UI.md 5d, muc 10). Dung lai hai hop
// thoai co san (R-A3, R-A4) va truyen du query can lam moi cua trang (R-A5).
export function ApplicationActionBar({ detail }: { detail: ApplicationHrDetail }) {
  const [inviteOpen, setInviteOpen] = useState(false)
  const [confirmTarget, setConfirmTarget] = useState<ApplicationStatusConfirmTarget | null>(null)
  const invalidateQueryKeys = applicationDetailInvalidateKeys(detail.id, detail.jobId)
  const application = { id: detail.id, candidateName: detail.candidateName }
  const actions = nextActionsFor(detail.status)
  const hasAnyAction = actions.canInvite || actions.canReject || actions.canHire

  return (
    <>
      {hasAnyAction ? (
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          {actions.canInvite && (
            <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setInviteOpen(true)}>
              Mời phỏng vấn
            </Button>
          )}
          {actions.canHire && (
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={() => setConfirmTarget({ application, targetStatus: 'HIRED' })}
            >
              Trúng tuyển
            </Button>
          )}
          {actions.canReject && (
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={() => setConfirmTarget({ application, targetStatus: 'REJECTED' })}
            >
              Từ chối
            </Button>
          )}
        </div>
      ) : (
        // Trang thai cuoi: mot dong chu trung tinh thay cho ba nut (R-A1, UI.md muc 7).
        <p className="text-sm text-m3-on-surface-variant">
          {detail.status === 'WITHDRAWN'
            ? 'Ứng viên đã rút đơn. Không còn thao tác nào.'
            : 'Đơn đã có kết quả cuối. Không còn thao tác nào.'}
        </p>
      )}

      <InterviewInvitationDialog
        application={inviteOpen ? application : null}
        jobId={detail.jobId}
        onOpenChange={(open) => !open && setInviteOpen(false)}
        invalidateQueryKeys={invalidateQueryKeys}
      />
      <ApplicationStatusConfirmDialog
        target={confirmTarget}
        jobId={detail.jobId}
        onOpenChange={(open) => !open && setConfirmTarget(null)}
        invalidateQueryKeys={invalidateQueryKeys}
      />
    </>
  )
}

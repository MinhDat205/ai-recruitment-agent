import type { QueryKey } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { extractErrorMessage } from '../../lib/httpError'
import { useChangeApplicationStatusMutation } from '../scoring/queries'

export type ConfirmableApplicationStatus = 'HIRED' | 'REJECTED'

// FR-H07 (E1, Dot 3) - nhan tieu de/mo ta cho hop thoai xac nhan Tu choi/Trung tuyen, dung CHUNG
// mot component cho ca hai (cung hinh dang, khac chu/targetStatus) - mau y het dialog "Rut don ung
// tuyen?" trong CandidateApplicationsPage.tsx.
const CONFIRM_STATUS_COPY: Record<
  ConfirmableApplicationStatus,
  { title: string; statusLabel: string; confirmLabel: string; pendingLabel: string }
> = {
  HIRED: {
    title: 'Xác nhận trúng tuyển?',
    statusLabel: 'Trúng tuyển',
    confirmLabel: 'Xác nhận trúng tuyển',
    pendingLabel: 'Đang lưu...',
  },
  REJECTED: {
    title: 'Từ chối ứng viên?',
    statusLabel: 'Bị từ chối',
    confirmLabel: 'Xác nhận từ chối',
    pendingLabel: 'Đang lưu...',
  },
}

export interface ApplicationStatusConfirmTarget {
  application: { id: string; candidateName: string }
  targetStatus: ConfirmableApplicationStatus
}

interface ApplicationStatusConfirmDialogProps {
  target: ApplicationStatusConfirmTarget | null
  jobId: string
  onOpenChange: (open: boolean) => void
  invalidateQueryKeys?: QueryKey[]
}

// FR-H09 R-A4 - hop xac nhan Tu choi/Trung tuyen CHUYEN nguyen van tu ApplicationsTab de danh sach
// theo Job va trang ho so don dung chung. Noi dung chu va hanh vi khong doi: dong hop thi reset
// mutation (xoa loi cu), gui thanh cong thi dong hop, loi 400/409 hien ngay trong hop.
// Mutation nam trong component (truoc nam o ApplicationsTab) - mau InterviewInvitationDialog.
// invalidateQueryKeys: query THEM can lam moi sau khi doi trang thai (mac dinh rong).
export function ApplicationStatusConfirmDialog({
  target,
  jobId,
  onOpenChange,
  invalidateQueryKeys,
}: ApplicationStatusConfirmDialogProps) {
  const changeStatusMutation = useChangeApplicationStatusMutation(jobId, invalidateQueryKeys)

  function closeConfirmDialog() {
    onOpenChange(false)
    changeStatusMutation.reset()
  }

  function confirmChangeStatus() {
    if (!target) return
    changeStatusMutation.mutate(
      { applicationId: target.application.id, status: target.targetStatus },
      { onSuccess: () => onOpenChange(false) },
    )
  }

  return (
    <Dialog open={target !== null} onOpenChange={(open) => !open && closeConfirmDialog()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{target && CONFIRM_STATUS_COPY[target.targetStatus].title}</DialogTitle>
          <DialogDescription>
            Hành động này không thể hoàn tác. Đơn ứng tuyển của{' '}
            <span className="font-medium text-m3-on-surface">{target?.application.candidateName}</span> sẽ chuyển sang
            trạng thái "{target && CONFIRM_STATUS_COPY[target.targetStatus].statusLabel}".
          </DialogDescription>
        </DialogHeader>
        {changeStatusMutation.isError && (
          <p className="text-sm text-m3-error">
            {extractErrorMessage(changeStatusMutation.error, 'Cập nhật trạng thái thất bại, vui lòng thử lại.')}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={closeConfirmDialog} disabled={changeStatusMutation.isPending}>
            Huỷ
          </Button>
          <Button type="button" onClick={confirmChangeStatus} disabled={changeStatusMutation.isPending}>
            {changeStatusMutation.isPending
              ? target && CONFIRM_STATUS_COPY[target.targetStatus].pendingLabel
              : target && CONFIRM_STATUS_COPY[target.targetStatus].confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { extractErrorMessage } from '../../lib/httpError'
import { useWithdrawApplicationMutation } from './queries'

interface WithdrawApplicationDialogProps {
  // Don dang xin rut; null = hop thoai dong.
  application: { id: string; jobTitle: string } | null
  onClose: () => void
}

// FR-U08 R-C3 - hop xac nhan rut don (FR-U06), tach NGUYEN VAN tu CandidateApplicationsPage: tieu de, noi
// dung, nhan nut, cau loi giu nguyen. Dung chinh useWithdrawApplicationMutation co san (khong tao mutation
// thu hai). Dong hop thoai thi reset trang thai loi cua mutation, nhu truoc.
export function WithdrawApplicationDialog({ application, onClose }: WithdrawApplicationDialogProps) {
  const withdrawMutation = useWithdrawApplicationMutation()

  const close = () => {
    onClose()
    withdrawMutation.reset()
  }

  const confirm = () => {
    if (!application) {
      return
    }
    withdrawMutation.mutate(application.id, {
      onSuccess: () => onClose(),
    })
  }

  return (
    <Dialog open={application !== null} onOpenChange={(open) => !open && close()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Rút đơn ứng tuyển?</DialogTitle>
          <DialogDescription>
            Hành động này không thể hoàn tác. Sau khi rút, bạn sẽ không thể nộp lại đơn cho vị trí{' '}
            <span className="font-medium text-m3-on-surface">{application?.jobTitle}</span> trong đợt tuyển hiện tại.
          </DialogDescription>
        </DialogHeader>
        {withdrawMutation.isError && (
          <p className="text-sm text-m3-error">
            {extractErrorMessage(withdrawMutation.error, 'Rút đơn thất bại, vui lòng thử lại.')}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={close} disabled={withdrawMutation.isPending}>
            Huỷ
          </Button>
          <Button type="button" onClick={confirm} disabled={withdrawMutation.isPending}>
            {withdrawMutation.isPending ? 'Đang rút đơn...' : 'Xác nhận rút đơn'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

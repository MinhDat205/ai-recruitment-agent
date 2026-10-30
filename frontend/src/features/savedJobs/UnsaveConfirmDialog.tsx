import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { SAVED_JOB_TEXT, confirmUnsaveText } from './savedJobTexts'

interface UnsaveConfirmDialogProps {
  open: boolean
  jobTitle: string
  pending: boolean
  errorMessage: string | null
  onConfirm: () => void
  onCancel: () => void
}

// FR-U16 UI.md 4e (Q5) - CHI hoi khi tin khong con OPEN: bo luu xong thi khong luu lai duoc (R-S7). Tin OPEN bo luu
// ngay, khong qua hop nay. Khuon WithdrawApplicationDialog (Radix lo focus trap, Esc).
export function UnsaveConfirmDialog({
  open,
  jobTitle,
  pending,
  errorMessage,
  onConfirm,
  onCancel,
}: UnsaveConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && !pending && onCancel()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{SAVED_JOB_TEXT.confirmTitle}</DialogTitle>
          <DialogDescription>{confirmUnsaveText(jobTitle)}</DialogDescription>
        </DialogHeader>
        {errorMessage && (
          <p role="status" className="text-sm text-m3-error">
            {errorMessage}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onCancel} disabled={pending}>
            {SAVED_JOB_TEXT.cancel}
          </Button>
          <Button type="button" variant="outline" onClick={onConfirm} disabled={pending}>
            {pending ? SAVED_JOB_TEXT.unsaving : SAVED_JOB_TEXT.unsave}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

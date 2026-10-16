import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { TALENT_POOL_TEXT } from './talentPoolLabels'

// FR-H15 UI.md 4e - xac nhan "Xoa kho" / "Go khoi kho". Nut xac nhan dung kieu Filled thuong, KHONG mau do: go mot ho so
// khoi kho khong phai phan quyet ve ung vien (UI_GUIDE muc 4, bang mau trung tinh).
interface ConfirmTalentPoolDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  message: string
  confirmLabel: string
  pendingLabel: string
  pending: boolean
  // Cau loi backend (hoac cau du phong) cua lan xac nhan gan nhat; null = chua co loi.
  error: string | null
  onConfirm: () => void
}

export function ConfirmTalentPoolDialog({
  open,
  onOpenChange,
  title,
  message,
  confirmLabel,
  pendingLabel,
  pending,
  error,
  onConfirm,
}: ConfirmTalentPoolDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(next) => !pending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription className="break-words text-m3-on-surface">{message}</DialogDescription>
        </DialogHeader>
        {error && (
          <p role="alert" className="text-sm text-m3-error">
            {error}
          </p>
        )}
        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            className="min-h-12 sm:min-h-0"
            disabled={pending}
            onClick={() => onOpenChange(false)}
          >
            {TALENT_POOL_TEXT.cancel}
          </Button>
          <Button type="button" className="min-h-12 sm:min-h-0" disabled={pending} onClick={onConfirm}>
            {pending ? pendingLabel : confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

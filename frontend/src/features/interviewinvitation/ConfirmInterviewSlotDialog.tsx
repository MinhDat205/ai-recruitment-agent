import { useRef } from 'react'
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
import { formatSlotTime, INVITATION_TEXT } from './invitationLabels'
import { useConfirmInterviewSlotMutation } from './queries'
import type { CandidateInterviewInvitationSlot } from './types'

interface ConfirmInterviewSlotDialogProps {
  applicationId: string
  // Khung ung vien vua chon; null = hop thoai dong.
  slot: CandidateInterviewInvitationSlot | null
  onClose: () => void
  // Chay khi hop thoai dong han (onCloseAutoFocus cua Radix). confirmed = dong do xac nhan thanh cong - noi goi chan focus
  // mac dinh va tu dat focus (L28); huy/dong thuong thi noi goi de Radix tra focus nhu binh thuong.
  onCloseAutoFocus: (event: Event, confirmed: boolean) => void
}

// FR-U10 UI.md 4c - hop xac nhan chon khung, cung cau truc RevokeTalentPoolConsentDialog (viet rieng, khong import).
// Focus dau vao "Huỷ": DialogFooter dat "Huỷ" truoc "Xác nhận", nut X render sau cung -> Radix tu focus "Huỷ" (L9), thao
// tac khong dao nguoc duoc nen tranh Enter vo y xac nhan. Dang gui: ca hai nut khoa, chan dong (Esc/X/bam ngoai di qua
// close() bi chan), khong goi mutate lan hai. Loi: cau backend trong hop thoai (fallback co dinh), hop thoai KHONG tu dong;
// mutation da lam moi I4 / trang chi tiet don. Dong hop thoai thi reset loi cua mutation.
export function ConfirmInterviewSlotDialog({ applicationId, slot, onClose, onCloseAutoFocus }: ConfirmInterviewSlotDialogProps) {
  const confirmMutation = useConfirmInterviewSlotMutation()
  const confirmedRef = useRef(false)

  const close = () => {
    if (confirmMutation.isPending) {
      return
    }
    onClose()
    confirmMutation.reset()
  }

  const confirm = () => {
    if (!slot || confirmMutation.isPending) {
      return
    }
    confirmMutation.mutate(
      { applicationId, slotId: slot.id },
      {
        onSuccess: () => {
          confirmedRef.current = true
          onClose()
          confirmMutation.reset()
        },
      },
    )
  }

  return (
    <Dialog open={slot !== null} onOpenChange={(open) => !open && close()}>
      <DialogContent
        className="sm:max-w-md"
        onCloseAutoFocus={(event) => {
          const confirmed = confirmedRef.current
          confirmedRef.current = false
          onCloseAutoFocus(event, confirmed)
        }}
      >
        <DialogHeader>
          <DialogTitle>{INVITATION_TEXT.confirmDialogTitle}</DialogTitle>
          <DialogDescription asChild>
            <div className="flex flex-col gap-2 break-words text-m3-on-surface">
              <p>{INVITATION_TEXT.confirmDialogIntro}</p>
              {slot && (
                <div className="flex flex-col gap-1 pl-4">
                  <p className="font-medium">{INVITATION_TEXT.confirmDialogTime(formatSlotTime(slot.scheduledAt))}</p>
                  <p className="break-all text-m3-on-surface-variant">
                    <span className="font-medium text-m3-on-surface">{INVITATION_TEXT.locationPrefix}</span>
                    {slot.location ?? INVITATION_TEXT.missingLocation}
                  </p>
                </div>
              )}
              <p>{INVITATION_TEXT.confirmDialogWarning}</p>
            </div>
          </DialogDescription>
        </DialogHeader>
        {confirmMutation.isError && (
          <p role="alert" className="text-sm break-words text-m3-error">
            {extractErrorMessage(confirmMutation.error, INVITATION_TEXT.confirmSlotFailed)}
          </p>
        )}
        <DialogFooter className="flex-row justify-end">
          <Button type="button" variant="outline" onClick={close} disabled={confirmMutation.isPending}>
            {INVITATION_TEXT.confirmDialogCancel}
          </Button>
          <Button type="button" onClick={confirm} disabled={confirmMutation.isPending}>
            {confirmMutation.isPending ? INVITATION_TEXT.confirmDialogConfirming : INVITATION_TEXT.confirmDialogConfirm}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

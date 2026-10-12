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
import { TALENT_POOL_TEXT } from '../candidateScreening/candidateScreeningLabels'
import { useRevokeTalentPoolConsentMutation } from './queries'

interface RevokeTalentPoolConsentDialogProps {
  // Don dang xin rut dong y; null = hop thoai dong.
  application: { id: string; companyName: string } | null
  onClose: () => void
  onRevoked: () => void
}

// Hop xac nhan rut dong y luu ho so (FR-U09 UI.md 4g) - cung cau truc WithdrawApplicationDialog (khong import, khong sua).
// Dang gui: ca hai nut khoa, khong goi mutate lan hai. 409 TALENT_POOL_CONSENT_NOT_ACTIVE / loi mang: hien cau backend
// (fallback cau co dinh) ngay trong hop thoai, mutation da lam moi E1. Dong hop thoai thi reset loi cua mutation.
export function RevokeTalentPoolConsentDialog({ application, onClose, onRevoked }: RevokeTalentPoolConsentDialogProps) {
  const revokeMutation = useRevokeTalentPoolConsentMutation()
  // Rut thanh cong: nut da mo hop thoai bien mat, Radix khong tra focus ve dau duoc - chan focus mac dinh khi dong va
  // de noi goi dat focus (onCloseAutoFocus chay SAU khi hop thoai dong han).
  const revokedRef = useRef(false)

  const close = () => {
    if (revokeMutation.isPending) {
      return
    }
    onClose()
    revokeMutation.reset()
  }

  const confirm = () => {
    if (!application || revokeMutation.isPending) {
      return
    }
    revokeMutation.mutate(application.id, {
      onSuccess: () => {
        revokedRef.current = true
        onClose()
        revokeMutation.reset()
      },
    })
  }

  return (
    <Dialog open={application !== null} onOpenChange={(open) => !open && close()}>
      <DialogContent
        className="sm:max-w-md"
        onCloseAutoFocus={(event) => {
          if (revokedRef.current) {
            revokedRef.current = false
            event.preventDefault()
            onRevoked()
          }
        }}
      >
        <DialogHeader>
          <DialogTitle>{TALENT_POOL_TEXT.dialogTitle}</DialogTitle>
          <DialogDescription className="break-words">
            <span className="font-medium text-m3-on-surface">{application?.companyName}</span>
            {TALENT_POOL_TEXT.dialogBodySuffix}
          </DialogDescription>
        </DialogHeader>
        {revokeMutation.isError && (
          <p role="alert" className="text-sm break-words text-m3-error">
            {extractErrorMessage(revokeMutation.error, TALENT_POOL_TEXT.revokeError)}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={close} disabled={revokeMutation.isPending}>
            {TALENT_POOL_TEXT.cancel}
          </Button>
          <Button type="button" onClick={confirm} disabled={revokeMutation.isPending}>
            {revokeMutation.isPending ? TALENT_POOL_TEXT.confirming : TALENT_POOL_TEXT.confirm}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

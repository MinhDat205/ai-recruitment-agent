import { useState } from 'react'
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
import { BLIND_REVIEW_TEXT } from './blindReviewLabels'
import { useUpdateBlindReviewMutation } from './queries'

// FR-H16 UI.md 4a - khoi "Che do an danh" o tab "Thong tin tin tuyen dung". Card RIENG dat duoi form tin, KHONG nam trong
// <form>: nut type="button", khong dung toi state cua React Hook Form nen khong lam form "dirty". Duong doi cong tac duy
// nhat la B1 (PATCH .../blind-review), khong gui qua PUT /hr/jobs/{id}.
interface BlindReviewCardProps {
  jobId: string
  enabled: boolean
}

export function BlindReviewCard({ jobId, enabled }: BlindReviewCardProps) {
  const mutation = useUpdateBlindReviewMutation(jobId)
  const [confirmOpen, setConfirmOpen] = useState(false)
  // L8 - KHONG hien thoi diem doi: GET Job khong tra changedAt, hien tu response B1 roi mat khi tai lai trong nhu loi.
  // Luu vet van ghi o DB (R-B3), khong co man xem (R-B5).

  const target = !enabled
  const pending = mutation.isPending
  const error = mutation.isError ? extractErrorMessage(mutation.error, BLIND_REVIEW_TEXT.toggleFailed) : null

  function openConfirm() {
    mutation.reset()
    setConfirmOpen(true)
  }

  async function confirm() {
    try {
      await mutation.mutateAsync(target)
      setConfirmOpen(false)
    } catch {
      // mutation.isError hien cau loi trong hop thoai; hop thoai khong dong (UI.md muc 6).
    }
  }

  // Le = --card-spacing cua Card cha (cung padding voi CardContent/CardFooter): mep card thang cot voi o nhap va nut
  // "Luu thay doi", co khoang cach voi dai nen CardFooter phia tren.
  return (
    <section
      aria-labelledby="blind-review-title"
      className="m-(--card-spacing) flex flex-col gap-3 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-4 sm:flex-row sm:items-end sm:justify-between"
    >
      <div className="flex min-w-0 flex-col gap-1">
        <h3 id="blind-review-title" className="text-base font-medium text-m3-on-surface">
          {BLIND_REVIEW_TEXT.cardTitle}
        </h3>
        <p aria-live="polite" className="text-sm text-m3-on-surface">
          {enabled ? BLIND_REVIEW_TEXT.stateOn : BLIND_REVIEW_TEXT.stateOff}
        </p>
        <p className="break-words text-sm text-m3-on-surface-variant">{BLIND_REVIEW_TEXT.description}</p>
      </div>
      <Button type="button" variant="outline" className="min-h-12 w-full shrink-0 sm:min-h-0 sm:w-auto" onClick={openConfirm}>
        {enabled ? BLIND_REVIEW_TEXT.disable : BLIND_REVIEW_TEXT.enable}
      </Button>

      <Dialog open={confirmOpen} onOpenChange={(next) => !pending && setConfirmOpen(next)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>
              {target ? BLIND_REVIEW_TEXT.confirmEnableTitle : BLIND_REVIEW_TEXT.confirmDisableTitle}
            </DialogTitle>
            <DialogDescription className="break-words text-m3-on-surface">
              {target ? BLIND_REVIEW_TEXT.confirmEnableMessage : BLIND_REVIEW_TEXT.confirmDisableMessage}
            </DialogDescription>
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
              onClick={() => setConfirmOpen(false)}
            >
              {BLIND_REVIEW_TEXT.cancel}
            </Button>
            <Button type="button" className="min-h-12 sm:min-h-0" disabled={pending} onClick={confirm}>
              {pending ? BLIND_REVIEW_TEXT.saving : target ? BLIND_REVIEW_TEXT.enable : BLIND_REVIEW_TEXT.disable}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  )
}

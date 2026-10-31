import { useId, useRef, useState } from 'react'
import { Info } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { extractErrorMessage } from '../../lib/httpError'
import { useDeclineReconsiderationMutation } from './queries'
import {
  HR_RECONSIDERATION_TEXT as TEXT,
  RECONSIDERATION_MAX_LENGTH,
  reconsiderationTextLength,
} from './reconsiderationLabels'

interface DeclineReconsiderationDialogProps {
  // Yeu cau dang xu ly (ten hoac nhan "Ứng viên #…" khi an danh); null = hop thoai dong.
  target: { applicationId: string; candidateName: string } | null
  jobId: string
  onClose: () => void
  onDeclined: () => void
}

// Hop thoai "Giữ quyết định từ chối" (FR-C09 UI.md 4g, H2). Phan hoi KHONG bat buoc va la NOI DUNG GUI UNG VIEN (R-V3) -
// khoi luu y noi ro, khong phai ghi chu noi bo. Bo dem/khoa nhu hop thoai ung vien (R-V2). Khong co soan nhap AI (muc 5).
// 409 RECONSIDERATION_NOT_OPEN / 400: hien cau backend trong hop thoai, mutation da lam moi H1 + E1 ho so don.
export function DeclineReconsiderationDialog({ target, jobId, onClose, onDeclined }: DeclineReconsiderationDialogProps) {
  const textareaId = useId()
  const counterId = useId()
  const errorId = useId()
  const [text, setText] = useState('')
  const declineMutation = useDeclineReconsiderationMutation(jobId)
  // Thanh cong: nut mo hop thoai bien mat (yeu cau khong con OPEN) - chan focus mac dinh, de noi goi dat focus (UI.md 4g).
  const declinedRef = useRef(false)

  const length = reconsiderationTextLength(text)
  const tooLong = length > RECONSIDERATION_MAX_LENGTH
  const isPending = declineMutation.isPending

  const close = () => {
    if (isPending) {
      return
    }
    setText('')
    onClose()
    declineMutation.reset()
  }

  const confirm = () => {
    if (!target || isPending || tooLong) {
      return
    }
    declineMutation.mutate(
      { applicationId: target.applicationId, response: text.length === 0 ? null : text },
      {
        onSuccess: () => {
          declinedRef.current = true
          setText('')
          onClose()
          declineMutation.reset()
        },
      },
    )
  }

  const describedBy = [counterId, tooLong || declineMutation.isError ? errorId : null].filter(Boolean).join(' ')

  return (
    <Dialog open={target !== null} onOpenChange={(next) => !next && close()}>
      <DialogContent
        className="max-h-[90vh] overflow-y-auto sm:max-w-lg"
        onCloseAutoFocus={(event) => {
          if (declinedRef.current) {
            declinedRef.current = false
            event.preventDefault()
            onDeclined()
          }
        }}
      >
        <DialogHeader>
          <DialogTitle className="break-words">
            {target ? TEXT.declineDialogTitle(target.candidateName) : null}
          </DialogTitle>
          <DialogDescription className="break-words">{TEXT.declineDialogDescription}</DialogDescription>
        </DialogHeader>

        <div
          role="note"
          className="flex items-start gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container px-4 py-3 text-sm text-m3-on-surface"
        >
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-m3-on-surface" aria-hidden="true" />
          <p className="min-w-0 break-words">{TEXT.declineDialogNotice}</p>
        </div>

        <div className="flex flex-col gap-2">
          <Label htmlFor={textareaId}>{TEXT.responseInputLabel}</Label>
          <Textarea
            id={textareaId}
            rows={5}
            value={text}
            onChange={(event) => setText(event.target.value)}
            readOnly={isPending}
            aria-describedby={describedBy}
            aria-invalid={tooLong || undefined}
            className="break-words"
          />
          <span
            id={counterId}
            className={`self-end text-sm ${tooLong ? 'text-m3-error' : 'text-m3-on-surface-variant'}`}
          >
            {length}/{RECONSIDERATION_MAX_LENGTH}
          </span>
          {tooLong ? (
            <p id={errorId} role="alert" className="text-sm break-words text-m3-error">
              {TEXT.tooLong}
            </p>
          ) : (
            declineMutation.isError && (
              <p id={errorId} role="alert" className="text-sm break-words text-m3-error">
                {extractErrorMessage(declineMutation.error, TEXT.declineError)}
              </p>
            )
          )}
        </div>

        <DialogFooter className="flex-row justify-end gap-2">
          <Button type="button" variant="outline" onClick={close} disabled={isPending}>
            {TEXT.cancel}
          </Button>
          <Button type="button" onClick={confirm} disabled={isPending || tooLong}>
            {isPending ? TEXT.submitting : TEXT.confirmDecline}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

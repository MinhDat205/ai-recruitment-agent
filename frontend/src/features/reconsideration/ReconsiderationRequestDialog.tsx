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
import { formatDateTimeVi } from '../../lib/date'
import { extractErrorMessage } from '../../lib/httpError'
import { useRequestReconsiderationMutation } from './queries'
import {
  CANDIDATE_RECONSIDERATION_TEXT as TEXT,
  RECONSIDERATION_MAX_LENGTH,
  reconsiderationTextLength,
} from './reconsiderationLabels'

interface ReconsiderationRequestDialogProps {
  // Don dang gui yeu cau + han chot do backend tra (R-T4, chi hien thi); null = hop thoai dong.
  application: { id: string; deadline: string } | null
  onClose: () => void
  onSubmitted: () => void
}

// Hop thoai "Yêu cầu xem xét lại" (FR-C09 UI.md 4c). Ly do khong bat buoc; bo dem dung cach backend dem (doi CRLF -> LF
// roi String.length, R-V2). O trong van gui duoc (reason null). Dang gui: o nhap va hai nut khoa. Loi 409/400: hien cau
// backend trong hop thoai (fallback cau co dinh muc 7), mutation da lam moi E1. Khung chep WithdrawApplicationDialog/
// RevokeTalentPoolConsentDialog (khong import).
export function ReconsiderationRequestDialog({ application, onClose, onSubmitted }: ReconsiderationRequestDialogProps) {
  const textareaId = useId()
  const counterId = useId()
  const hintId = useId()
  const errorId = useId()
  const [text, setText] = useState('')
  const requestMutation = useRequestReconsiderationMutation()
  // Gui thanh cong: nut mo hop thoai bien mat (khoi doi sang OPEN), Radix khong tra focus ve dau duoc - chan focus mac
  // dinh khi dong va de noi goi dat focus vao tieu de khoi (UI.md 4c, muc 9).
  const submittedRef = useRef(false)

  const open = application !== null

  const length = reconsiderationTextLength(text)
  const tooLong = length > RECONSIDERATION_MAX_LENGTH
  const isPending = requestMutation.isPending

  const close = () => {
    if (isPending) {
      return
    }
    // Dong hop thoai (Huy, Esc, bam ra ngoai) thi bo noi dung dang go - lan mo sau bat dau tu o trong.
    setText('')
    onClose()
    requestMutation.reset()
  }

  const submit = () => {
    if (!application || isPending || tooLong) {
      return
    }
    requestMutation.mutate(
      { applicationId: application.id, reason: text.length === 0 ? null : text },
      {
        onSuccess: () => {
          submittedRef.current = true
          setText('')
          onClose()
          requestMutation.reset()
        },
      },
    )
  }

  const describedBy = [hintId, counterId, tooLong || requestMutation.isError ? errorId : null]
    .filter(Boolean)
    .join(' ')

  return (
    <Dialog open={open} onOpenChange={(next) => !next && close()}>
      <DialogContent
        className="max-h-[90vh] overflow-y-auto sm:max-w-lg"
        onCloseAutoFocus={(event) => {
          if (submittedRef.current) {
            submittedRef.current = false
            event.preventDefault()
            onSubmitted()
          }
        }}
      >
        <DialogHeader>
          <DialogTitle>{TEXT.dialogTitle}</DialogTitle>
          <DialogDescription className="break-words">{TEXT.dialogDescription}</DialogDescription>
        </DialogHeader>

        <div
          role="note"
          className="flex items-start gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container px-4 py-3 text-sm text-m3-on-surface"
        >
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-m3-on-surface" aria-hidden="true" />
          <p className="min-w-0 break-words">
            {application ? TEXT.dialogNotice(formatDateTimeVi(application.deadline)) : null}
          </p>
        </div>

        <div className="flex flex-col gap-2">
          <Label htmlFor={textareaId}>{TEXT.reasonInputLabel}</Label>
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
          <div className="flex items-start justify-between gap-3 text-sm">
            <p id={hintId} className="min-w-0 break-words text-m3-on-surface-variant">
              {TEXT.reasonHint}
            </p>
            <span id={counterId} className={`shrink-0 ${tooLong ? 'text-m3-error' : 'text-m3-on-surface-variant'}`}>
              {length}/{RECONSIDERATION_MAX_LENGTH}
            </span>
          </div>
          {tooLong ? (
            <p id={errorId} role="alert" className="text-sm break-words text-m3-error">
              {TEXT.tooLong}
            </p>
          ) : (
            requestMutation.isError && (
              <p id={errorId} role="alert" className="text-sm break-words text-m3-error">
                {extractErrorMessage(requestMutation.error, TEXT.submitError)}
              </p>
            )
          )}
        </div>

        <DialogFooter className="flex-row justify-end gap-2">
          <Button type="button" variant="outline" onClick={close} disabled={isPending}>
            {TEXT.cancel}
          </Button>
          <Button type="button" onClick={submit} disabled={isPending || tooLong}>
            {isPending ? TEXT.submitting : TEXT.submit}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

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
import { JOB_DRAFT_TEXT } from './jobDraftLabels'

// UI.md 4f (Q11) - "Dien vao bieu mau" khi co truong HR da sua tay. Focus mac dinh "Chi dien truong chua sua" (lua chon
// khong mat du lieu); Esc / bam ra ngoai = dong, khong dien gi.
export function OverwriteFieldsDialog({
  open,
  editedLabels,
  onFillUntouched,
  onOverwriteAll,
  onCancel,
}: {
  open: boolean
  editedLabels: string[]
  onFillUntouched: () => void
  onOverwriteAll: () => void
  onCancel: () => void
}) {
  const keepButtonRef = useRef<HTMLButtonElement>(null)

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onCancel()}>
      <DialogContent
        showCloseButton={false}
        className="sm:max-w-md"
        onOpenAutoFocus={(event) => {
          event.preventDefault()
          keepButtonRef.current?.focus()
        }}
      >
        <DialogHeader>
          <DialogTitle>{JOB_DRAFT_TEXT.overwriteTitle}</DialogTitle>
          <DialogDescription className="break-words">
            {JOB_DRAFT_TEXT.overwriteBody(editedLabels.length, editedLabels.join(', '))}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button ref={keepButtonRef} type="button" variant="outline" className="min-h-10" onClick={onFillUntouched}>
            {JOB_DRAFT_TEXT.overwriteKeep}
          </Button>
          <Button type="button" className="min-h-10" onClick={onOverwriteAll}>
            {JOB_DRAFT_TEXT.overwriteAll}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

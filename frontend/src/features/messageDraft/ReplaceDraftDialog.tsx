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
import { DRAFT_TEXT } from './draftLabels'

// UI.md muc 4f (Q4) - o nhap dang co chu khi bam "Dung ban nhap". Focus vao "Giu noi dung dang soan" khi mo (lua chon
// khong mat du lieu); Esc / bam ra ngoai = giu. Tep da chon khong bi dung trong moi truong hop.
export function ReplaceDraftDialog({
  open,
  onKeep,
  onReplace,
}: {
  open: boolean
  onKeep: () => void
  onReplace: () => void
}) {
  const keepButtonRef = useRef<HTMLButtonElement>(null)

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onKeep()}>
      <DialogContent
        showCloseButton={false}
        className="sm:max-w-md"
        onOpenAutoFocus={(event) => {
          event.preventDefault()
          keepButtonRef.current?.focus()
        }}
      >
        <DialogHeader>
          <DialogTitle>{DRAFT_TEXT.replaceTitle}</DialogTitle>
          <DialogDescription>{DRAFT_TEXT.replaceDescription}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button ref={keepButtonRef} type="button" variant="outline" onClick={onKeep}>
            {DRAFT_TEXT.keepCurrent}
          </Button>
          <Button type="button" onClick={onReplace}>
            {DRAFT_TEXT.replaceWithDraft}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

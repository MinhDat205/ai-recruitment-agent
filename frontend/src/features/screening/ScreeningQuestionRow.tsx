import { ArrowDown, ArrowUp, Pencil, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { ScreeningQuestionFormDialog } from './ScreeningQuestionFormDialog'
import { SCREENING_QUESTION_TYPE_LABELS, SCREENING_TEXT } from './screeningLabels'
import type { ScreeningQuestionResponse } from './types'

export type MoveDirection = 'up' | 'down'

// Duoi sm vung cham >= 48px (UI_GUIDE muc 1h); tu sm tro len cung kich thuoc nut Sua tieu chi cua tab Rubric.
const ICON_BUTTON_SIZE = 'size-12 sm:size-7'

interface ScreeningQuestionRowProps {
  jobId: string
  question: ScreeningQuestionResponse
  position: number
  isFirst: boolean
  isLast: boolean
  moveDisabled: boolean
  onMove: (direction: MoveDirection) => void
  // Gan ref cho nut len/xuong de tab dat lai focus vao dung nut sau khi doi thu tu (UI.md muc 9).
  registerMoveButton: (direction: MoveDirection, element: HTMLButtonElement | null) => void
  deletePending: boolean
  onDelete: (onSettled: () => void) => void
}

export function ScreeningQuestionRow({
  jobId,
  question,
  position,
  isFirst,
  isLast,
  moveDisabled,
  onMove,
  registerMoveButton,
  deletePending,
  onDelete,
}: ScreeningQuestionRowProps) {
  const [editOpen, setEditOpen] = useState(false)
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)

  const meta = `${SCREENING_QUESTION_TYPE_LABELS[question.questionType]} · ${
    question.required ? SCREENING_TEXT.required : SCREENING_TEXT.notRequired
  }`

  return (
    <li className="flex flex-col gap-3 border-t border-m3-outline-variant p-4 first:border-t-0 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex min-w-0 flex-1 gap-1 break-words">
        <span className="shrink-0 text-m3-title-md text-m3-on-surface">{position}.</span>
        <div className="flex min-w-0 flex-col gap-1">
          <p className="text-m3-title-md text-m3-on-surface">{question.questionText}</p>
          <p className="text-sm text-m3-on-surface-variant">{meta}</p>
          {question.questionType === 'SINGLE_CHOICE' && question.options.length > 0 && (
            <p className="text-sm text-m3-on-surface-variant">{question.options.join(' · ')}</p>
          )}
        </div>
      </div>

      <div className="flex shrink-0 flex-wrap gap-2 pl-5 sm:pl-0">
        {/* Cau dau khong render nut len, cau cuoi khong render nut xuong; tu sm giu cho trong de cot nut thang hang. */}
        {isFirst ? (
          <span className={`hidden sm:block ${ICON_BUTTON_SIZE}`} aria-hidden="true" />
        ) : (
          <Button
            ref={(element) => registerMoveButton('up', element)}
            type="button"
            variant="outline"
            size="icon-sm"
            className={ICON_BUTTON_SIZE}
            aria-label={SCREENING_TEXT.moveUp(position)}
            disabled={moveDisabled}
            onClick={() => onMove('up')}
          >
            <ArrowUp />
          </Button>
        )}
        {isLast ? (
          <span className={`hidden sm:block ${ICON_BUTTON_SIZE}`} aria-hidden="true" />
        ) : (
          <Button
            ref={(element) => registerMoveButton('down', element)}
            type="button"
            variant="outline"
            size="icon-sm"
            className={ICON_BUTTON_SIZE}
            aria-label={SCREENING_TEXT.moveDown(position)}
            disabled={moveDisabled}
            onClick={() => onMove('down')}
          >
            <ArrowDown />
          </Button>
        )}
        <Button
          type="button"
          variant="outline"
          size="icon-sm"
          className={ICON_BUTTON_SIZE}
          aria-label={SCREENING_TEXT.edit(position)}
          onClick={() => setEditOpen(true)}
        >
          <Pencil />
        </Button>
        <Dialog open={confirmDeleteOpen} onOpenChange={setConfirmDeleteOpen}>
          <DialogTrigger asChild>
            <Button
              type="button"
              variant="outline"
              size="icon-sm"
              aria-label={SCREENING_TEXT.remove(position)}
              className={`${ICON_BUTTON_SIZE} border-m3-error text-m3-on-surface hover:bg-m3-surface-container`}
            >
              <Trash2 className="text-m3-error" aria-hidden="true" />
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{SCREENING_TEXT.deleteTitle}</DialogTitle>
              <DialogDescription>{SCREENING_TEXT.deleteBody}</DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setConfirmDeleteOpen(false)}>
                {SCREENING_TEXT.cancel}
              </Button>
              <Button
                type="button"
                variant="destructive"
                disabled={deletePending}
                // Dong hop xac nhan ca khi loi: loi S4 hien duoi danh sach (UI.md muc 6).
                onClick={() => onDelete(() => setConfirmDeleteOpen(false))}
              >
                {deletePending ? SCREENING_TEXT.deleting : SCREENING_TEXT.deleteConfirm}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      <ScreeningQuestionFormDialog jobId={jobId} open={editOpen} onOpenChange={setEditOpen} question={question} />
    </li>
  )
}

import { AlertCircle, Plus } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { CardContent } from '@/components/ui/card'
import { extractErrorMessage } from '../../lib/httpError'
import {
  useDeleteScreeningQuestionMutation,
  useReorderScreeningQuestionsMutation,
  useScreeningQuestionsQuery,
} from './queries'
import { ScreeningNotice } from './ScreeningNotice'
import { ScreeningQuestionFormDialog } from './ScreeningQuestionFormDialog'
import { ScreeningQuestionRow, type MoveDirection } from './ScreeningQuestionRow'
import { SCREENING_TEXT } from './screeningLabels'

const ANNOUNCE_TEXT_MAX = 40

function shorten(text: string): string {
  const chars = [...text]
  return chars.length <= ANNOUNCE_TEXT_MAX ? text : `${chars.slice(0, ANNOUNCE_TEXT_MAX).join('')}…`
}

function moveButtonKey(questionId: string, direction: MoveDirection): string {
  return `${questionId}:${direction}`
}

function ListSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-hidden="true">
      {[0, 1, 2].map((i) => (
        <div key={i} className="h-14 animate-pulse rounded-m3-xs bg-m3-surface-container" />
      ))}
    </div>
  )
}

// FR-H10 - tab "Cau hoi sang loc" o trang sua Job (UI.md 4a, 4b, 4f; S1-S5). Khong co trang thai "AI dang xu ly":
// FR-H10 khong goi AI.
export function ScreeningQuestionsTab({ jobId }: { jobId: string }) {
  const { data, isLoading, isError, refetch } = useScreeningQuestionsQuery(jobId)
  const reorderMutation = useReorderScreeningQuestionsMutation(jobId)
  const deleteMutation = useDeleteScreeningQuestionMutation(jobId)
  const [addOpen, setAddOpen] = useState(false)
  const [listError, setListError] = useState<string | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const moveButtons = useRef(new Map<string, HTMLButtonElement>())
  // Nut can nhan lai focus sau khi S5 tra ve (UI.md muc 9). Ref, khong state: chi doc trong effect.
  const pendingFocus = useRef<{ questionId: string; direction: MoveDirection } | null>(null)

  const questions = data ? [...data.questions].sort((a, b) => a.displayOrder - b.displayOrder) : []

  // Sau khi danh sach moi tu backend da render va nut het khoa: dat focus vao CUNG nut o vi tri moi; neu nut do khong
  // con (cau thanh dau/cuoi) thi dat vao nut con lai cua cau do.
  useEffect(() => {
    const target = pendingFocus.current
    if (!target || reorderMutation.isPending) {
      return
    }
    pendingFocus.current = null
    const other: MoveDirection = target.direction === 'up' ? 'down' : 'up'
    const element =
      moveButtons.current.get(moveButtonKey(target.questionId, target.direction)) ??
      moveButtons.current.get(moveButtonKey(target.questionId, other))
    element?.focus()
  }, [data, reorderMutation.isPending])

  function handleMove(index: number, direction: MoveDirection) {
    const swapWith = direction === 'up' ? index - 1 : index + 1
    if (swapWith < 0 || swapWith >= questions.length) {
      return
    }
    const ids = questions.map((q) => q.id)
    ;[ids[index], ids[swapWith]] = [ids[swapWith], ids[index]]
    const moved = questions[index]
    setListError(null)
    pendingFocus.current = { questionId: moved.id, direction }
    reorderMutation.mutate(ids, {
      onSuccess: () => setAnnouncement(SCREENING_TEXT.moved(shorten(moved.questionText), swapWith + 1)),
      onError: (error) => {
        pendingFocus.current = null
        setListError(extractErrorMessage(error, SCREENING_TEXT.reorderError))
      },
    })
  }

  function handleDelete(questionId: string, onSettled: () => void) {
    setListError(null)
    deleteMutation.mutate(questionId, {
      onError: (error) => setListError(extractErrorMessage(error, SCREENING_TEXT.deleteError)),
      onSettled,
    })
  }

  const maxQuestions = data?.maxQuestions
  const limitReached = data !== undefined && questions.length >= data.maxQuestions
  const limitReasonId = `screening-limit-${jobId}`

  return (
    <CardContent className="flex min-w-0 flex-col gap-4 pt-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 flex-col gap-1">
          <h2 className="text-m3-title-md text-m3-on-surface">{SCREENING_TEXT.tabTitle}</h2>
          {data && maxQuestions !== undefined && (
            <p className="text-sm text-m3-on-surface-variant">{SCREENING_TEXT.counter(questions.length, maxQuestions)}</p>
          )}
          {limitReached && maxQuestions !== undefined && (
            <p id={limitReasonId} className="text-sm text-m3-on-surface-variant">
              {SCREENING_TEXT.limitReached(maxQuestions)}
            </p>
          )}
        </div>
        <Button
          type="button"
          className="min-h-12 w-full sm:min-h-0 sm:w-auto"
          disabled={!data || limitReached}
          aria-describedby={limitReached ? limitReasonId : undefined}
          onClick={() => setAddOpen(true)}
        >
          <Plus /> {SCREENING_TEXT.add}
        </Button>
      </div>

      <ScreeningNotice />

      {isLoading && <ListSkeleton />}

      {!isLoading && (isError || !data) && (
        <div role="alert" className="flex flex-col items-start gap-2">
          <p className="flex items-start gap-1.5 text-sm text-m3-on-surface">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
            {SCREENING_TEXT.loadError}
          </p>
          <Button type="button" variant="outline" size="sm" onClick={() => void refetch()}>
            {SCREENING_TEXT.retry}
          </Button>
        </div>
      )}

      {data && questions.length === 0 && (
        <div className="flex flex-col items-start gap-3">
          <p className="text-sm text-m3-on-surface">{SCREENING_TEXT.empty}</p>
          <Button type="button" className="min-h-12 w-full sm:min-h-0 sm:w-auto" onClick={() => setAddOpen(true)}>
            <Plus /> {SCREENING_TEXT.addFirst}
          </Button>
        </div>
      )}

      {data && questions.length > 0 && (
        <ol className="rounded-m3-sm border border-m3-outline-variant bg-m3-surface">
          {questions.map((question, index) => (
            <ScreeningQuestionRow
              key={question.id}
              jobId={jobId}
              question={question}
              position={index + 1}
              isFirst={index === 0}
              isLast={index === questions.length - 1}
              moveDisabled={reorderMutation.isPending}
              onMove={(direction) => handleMove(index, direction)}
              registerMoveButton={(direction, element) => {
                const key = moveButtonKey(question.id, direction)
                if (element) {
                  moveButtons.current.set(key, element)
                } else {
                  moveButtons.current.delete(key)
                }
              }}
              deletePending={deleteMutation.isPending}
              onDelete={(onSettled) => handleDelete(question.id, onSettled)}
            />
          ))}
        </ol>
      )}

      {listError && (
        <div role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          <p className="min-w-0 break-words">{listError}</p>
        </div>
      )}

      <p aria-live="polite" className="sr-only">
        {announcement}
      </p>

      <ScreeningQuestionFormDialog jobId={jobId} open={addOpen} onOpenChange={setAddOpen} />
    </CardContent>
  )
}

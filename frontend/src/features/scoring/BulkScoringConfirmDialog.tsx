import { isAxiosError } from 'axios'
import { AlertCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { extractErrorMessage } from '../../lib/httpError'
import { useRubricQuery } from '../rubric/ownerQueries'
import { BULK_SCORING_LIMIT, BULK_SCORING_TEXT } from './bulkScoringTexts'

interface BulkScoringConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  jobId: string
  scorableCount: number
  isPending: boolean
  error: unknown
  onConfirm: () => void
}

// 4xx (409 RUBRIC_INCOMPLETE, 404, 403, 429): cau backend nguyen van (R-F6); mang/5xx: cau co dinh UI.md muc 7.
function errorMessage(error: unknown): string {
  if (isAxiosError(error) && error.response && error.response.status < 500) {
    return extractErrorMessage(error, BULK_SCORING_TEXT.networkError)
  }
  return BULK_SCORING_TEXT.networkError
}

// FR-H04 mo rong - hop xac nhan (UI.md muc 4c, 6). Mutation nam o BulkScoringButton (nut ngoai cung can trang thai dang
// gui, R-F2); hop chi hien uoc tinh va goi onConfirm. Moi con so tinh voi k = min(N, 50) (R-F3).
export function BulkScoringConfirmDialog({
  open,
  onOpenChange,
  jobId,
  scorableCount,
  isPending,
  error,
  onConfirm,
}: BulkScoringConfirmDialogProps) {
  // Q8 - cung khoa ['hr-rubric', jobId] voi tab Rubric; khong them endpoint, khong them truong vao response.
  const { data: rubric } = useRubricQuery(jobId)
  const count = Math.min(scorableCount, BULK_SCORING_LIMIT)
  const criteriaCount = rubric?.criteria.length

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="bg-m3-surface text-m3-on-surface sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{BULK_SCORING_TEXT.dialogTitle}</DialogTitle>
        </DialogHeader>
        <ul className="flex list-disc flex-col gap-2 pl-5 text-sm text-m3-on-surface">
          <li>
            {BULK_SCORING_TEXT.applicationCount(count)}
            {scorableCount > BULK_SCORING_LIMIT && (
              <p className="mt-1 text-m3-on-surface-variant">
                {BULK_SCORING_TEXT.overLimit(scorableCount - BULK_SCORING_LIMIT)}
              </p>
            )}
          </li>
          {/* Rubric dang tai/loi: van cho bam xac nhan - backend kiem rubric (R-F6). */}
          {criteriaCount === undefined ? (
            <li>{BULK_SCORING_TEXT.aiCallsUnknown}</li>
          ) : (
            <li>
              {BULK_SCORING_TEXT.aiCalls(count * (criteriaCount + 1))}
              <p className="mt-1 text-m3-on-surface-variant">{BULK_SCORING_TEXT.aiCallsNote(count, criteriaCount)}</p>
            </li>
          )}
          <li>{BULK_SCORING_TEXT.duration(count)}</li>
          <li>{BULK_SCORING_TEXT.polling}</li>
        </ul>
        {error !== null && error !== undefined && (
          <div role="alert" className="flex items-start gap-1.5 text-sm text-m3-error">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <p className="min-w-0 break-words">{errorMessage(error)}</p>
          </div>
        )}
        {/* DialogFooter < sm la flex-col-reverse: nut "Cham tat ca" (khai sau) nam tren (UI.md muc 8). */}
        <DialogFooter>
          <Button type="button" variant="outline" className="min-h-12 sm:min-h-0" onClick={() => onOpenChange(false)} disabled={isPending}>
            {BULK_SCORING_TEXT.cancel}
          </Button>
          <Button
            type="button"
            className="min-h-12 bg-m3-primary text-m3-on-primary hover:bg-m3-primary/90 sm:min-h-0"
            onClick={onConfirm}
            disabled={isPending}
          >
            {isPending ? BULK_SCORING_TEXT.confirming : BULK_SCORING_TEXT.confirm}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

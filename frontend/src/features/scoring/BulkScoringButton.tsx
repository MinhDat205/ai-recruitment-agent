import { useState } from 'react'
import { ListChecks } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { JobOwnerResponse } from '../jobs/ownerTypes'
import { BulkScoringConfirmDialog } from './BulkScoringConfirmDialog'
import { BULK_SCORING_REASON_ID, BULK_SCORING_TEXT, skippedSummary } from './bulkScoringTexts'
import { useBulkCreateScoringRunsMutation } from './queries'
import { isBulkScorable } from './scoringRules'
import type { ApplicationHrListItem, BulkScoringRunResult } from './types'

function resultText(result: BulkScoringRunResult): string {
  const parts = [
    result.created > 0 ? BULK_SCORING_TEXT.resultCreated(result.created) : BULK_SCORING_TEXT.resultNoneCreated,
    skippedSummary(result.skipped),
    result.remaining > 0 ? BULK_SCORING_TEXT.resultRemaining(result.remaining) : null,
  ]
  return parts.filter((part): part is string => part !== null).join(' ')
}

// FR-H04 mo rong (UI.md muc 4a, 4b, 5a) - nut "Cham tat ca (N)" + cau ly do khi khoa + cau ket qua. Tra ve fragment de nut
// va cau ket qua la con truc tiep cua hang "Sap xep theo" (ApplicationsTab): cau ket qua basis-full nen xuong dong duoi
// hang nut. State nam o day: roi tab thi component unmount nen cau ket qua mat (R-F5).
export function BulkScoringButton({
  jobId,
  job,
  applications,
}: {
  jobId: string
  job: JobOwnerResponse | undefined
  applications: ApplicationHrListItem[]
}) {
  const [dialogOpen, setDialogOpen] = useState(false)
  const [result, setResult] = useState<BulkScoringRunResult | null>(null)
  const bulkMutation = useBulkCreateScoringRunsMutation(jobId)

  // R-F1 - N chi dem bang isBulkScorable.
  const scorableCount = job
    ? applications.filter((application) => isBulkScorable(application, job.recruitmentCycle)).length
    : 0
  // R-F2 - thu tu: chua tai tin -> tin da xoa mem (R-P3) -> N = 0.
  const disabledReason = !job
    ? BULK_SCORING_TEXT.reasonJobLoading
    : job.deletedAt !== null
      ? BULK_SCORING_TEXT.reasonJobDeleted
      : scorableCount === 0
        ? BULK_SCORING_TEXT.reasonNoneScorable
        : undefined

  // R-F5 - cau ket qua cu mat khi HR bam lai.
  function openDialog() {
    setResult(null)
    bulkMutation.reset()
    setDialogOpen(true)
  }

  // Dang gui: khong dong bang Esc/bam nen/nut X (UI.md muc 6).
  function handleOpenChange(open: boolean) {
    if (!open && bulkMutation.isPending) {
      return
    }
    setDialogOpen(open)
  }

  // Nut xac nhan khoa khi isPending nen khong co request thu hai.
  function confirm() {
    bulkMutation.mutate(undefined, {
      onSuccess: (data) => {
        setResult(data)
        setDialogOpen(false)
      },
    })
  }

  return (
    <>
      <Button
        type="button"
        className="min-h-12 w-full bg-m3-primary-container text-m3-on-primary-container hover:bg-m3-primary-container/80 disabled:opacity-38 sm:h-10 sm:min-h-0 sm:w-auto"
        disabled={Boolean(disabledReason) || bulkMutation.isPending}
        aria-describedby={disabledReason ? BULK_SCORING_REASON_ID : undefined}
        onClick={openDialog}
      >
        <ListChecks className="h-4 w-4" aria-hidden="true" />
        {BULK_SCORING_TEXT.button(scorableCount)}
      </Button>
      {disabledReason && (
        <span id={BULK_SCORING_REASON_ID} className="sr-only">
          {disabledReason}
        </span>
      )}
      {/* Vung role="status" luon co trong DOM de trinh doc man hinh doc khi noi dung doi (khuon aria-live cua
          CompareSelectionBar); chua co ket qua thi rong va sr-only, khong chiem cho trong hang flex. Chu trung tinh,
          khong to mau theo so don bo qua (R-F5, CLAUDE.md muc 8). */}
      <p role="status" className={result ? 'basis-full break-words text-sm text-m3-on-surface' : 'sr-only'}>
        {result ? resultText(result) : ''}
      </p>
      <BulkScoringConfirmDialog
        open={dialogOpen}
        onOpenChange={handleOpenChange}
        jobId={jobId}
        scorableCount={scorableCount}
        isPending={bulkMutation.isPending}
        error={bulkMutation.error}
        onConfirm={confirm}
      />
    </>
  )
}

import { AlertCircle, Building2 } from 'lucide-react'
import { useId, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { formatDateTimeVi, formatDeadline } from '@/lib/date'
import { ApplicationStatusBadge } from '../applications/ApplicationStatusBadge'
import { jobLocationText } from '../jobs/catalogDisplay'
import { formatSalary } from '../jobs/formatSalary'
import { JobAvailabilityBadge } from '../jobs/JobAvailabilityBadge'
import { useIsTogglingSavedJob, useUnsaveJobMutation } from './queries'
import { SAVED_JOB_TEXT, UNAVAILABLE_REASONS, savedAtText } from './savedJobTexts'
import type { SavedJob } from './types'
import { UnsaveConfirmDialog } from './UnsaveConfirmDialog'

interface SavedJobItemProps {
  item: SavedJob
  // Goi sau khi bo luu thanh cong (hang sap bien mat) - trang chuyen focus ve tieu de (UI.md muc 9).
  onRemoved: () => void
}

// FR-U16 UI.md 4c/4d, REQUIREMENT R-V4..R-V8. Thu tu do backend tra (R-V1), khong sap lai. Khong diem, khong hang, khong
// "phu hop" (R-G2). Khong dung JobCard (L7): tieu de chi la lien ket khi OPEN, va hang co nhieu nut.
export function SavedJobItem({ item, onRemoved }: SavedJobItemProps) {
  const reasonId = useId()
  const unsaveMutation = useUnsaveJobMutation(item.jobId)
  const toggling = useIsTogglingSavedJob(item.jobId)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const isOpen = item.availability === 'OPEN'
  const summary = item.summary
  // R-V4 - co don o chu ky hien tai thang moi tinh trang; khong co don va tin khong mo -> cau ly do.
  const reason = !item.application && item.availability !== 'OPEN' ? UNAVAILABLE_REASONS[item.availability] : null

  function unsave() {
    setErrorMessage(null)
    unsaveMutation.mutate(undefined, {
      onSuccess: () => {
        setConfirmOpen(false)
        onRemoved()
      },
      onError: () => setErrorMessage(SAVED_JOB_TEXT.unsaveErrorFallback),
    })
  }

  // Q5 - chi hoi xac nhan khi tin khong con mo (bo luu xong khong luu lai duoc).
  function handleUnsaveClick() {
    if (isOpen) {
      unsave()
    } else {
      setErrorMessage(null)
      setConfirmOpen(true)
    }
  }

  return (
    <li className="flex gap-4 border-b border-m3-outline-variant py-4 first:pt-0 last:border-b-0 last:pb-0">
      <div className="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-(--radius-badge) border border-m3-outline-variant bg-m3-surface-container sm:h-16 sm:w-16">
        {item.company?.logoUrl ? (
          <img src={item.company.logoUrl} alt={item.company.name} className="h-full w-full object-cover" />
        ) : (
          <Building2 className="h-6 w-6 text-m3-on-surface-variant" aria-hidden="true" />
        )}
      </div>

      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="min-w-0 break-words text-base font-medium text-m3-on-surface">
            {/* R-V5 - lien ket sang trang tin CHI khi OPEN (gia tri khac -> /jobs/:id bao "Khong tim thay"). */}
            {isOpen ? (
              <Link to={`/jobs/${item.jobId}`} className="hover:text-m3-primary hover:underline">
                {item.title}
              </Link>
            ) : (
              item.title
            )}
          </h2>
          <JobAvailabilityBadge availability={item.availability} />
        </div>
        {item.company && <p className="break-words text-sm text-m3-on-surface-variant">{item.company.name}</p>}

        {/* R-V6 - tin UNAVAILABLE khong co summary: khong dong tom tat. R-V7 - con lai: ban HIEN TAI cua tin. */}
        {summary && (
          <p className="text-sm text-m3-on-surface">
            <span className="font-medium text-m3-tertiary">{formatSalary(summary) ?? SAVED_JOB_TEXT.salaryUnlisted}</span>
            {' · '}
            {jobLocationText(summary) ?? SAVED_JOB_TEXT.missingData}
            {' · '}
            Hạn nộp: {formatDeadline(summary.deadline)}
          </p>
        )}

        <div className="mt-1 flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
          <p className="text-sm text-m3-on-surface-variant">{savedAtText(formatDateTimeVi(item.savedAt))}</p>

          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
            {item.application ? (
              <div className="flex flex-wrap items-center gap-2 text-sm text-m3-on-surface">
                <span>{SAVED_JOB_TEXT.applied}</span>
                <ApplicationStatusBadge status={item.application.status} />
                <Link
                  to={`/candidate/applications/${item.application.id}`}
                  className="font-medium text-m3-primary hover:underline"
                >
                  {SAVED_JOB_TEXT.viewApplication}
                </Link>
              </div>
            ) : isOpen ? (
              // R-V8 - chi dieu huong sang luong nop don co san, khong form thu hai.
              <Link
                to={`/jobs/${item.jobId}/apply`}
                className="inline-flex h-10 w-full items-center justify-center rounded-md bg-m3-tertiary px-6 text-sm font-medium text-m3-on-tertiary sm:w-auto"
              >
                {SAVED_JOB_TEXT.apply}
              </Link>
            ) : (
              // R-V6 - tin da go: khong nut Ung tuyen, chi cau ly do (ben duoi).
              item.availability !== 'UNAVAILABLE' && (
                <Button type="button" variant="outline" className="w-full sm:w-auto" disabled aria-describedby={reasonId}>
                  {SAVED_JOB_TEXT.apply}
                </Button>
              )
            )}
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={handleUnsaveClick}
              disabled={toggling}
            >
              {toggling && !confirmOpen ? SAVED_JOB_TEXT.unsaving : SAVED_JOB_TEXT.unsave}
            </Button>
          </div>
        </div>

        {reason && (
          <p id={reasonId} className="text-sm text-m3-on-surface-variant">
            {reason}
          </p>
        )}
        {errorMessage && !confirmOpen && (
          <p role="status" className="flex items-center gap-1 text-sm text-m3-on-surface">
            <AlertCircle className="h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
            {errorMessage}
          </p>
        )}
      </div>

      <UnsaveConfirmDialog
        open={confirmOpen}
        jobTitle={item.title}
        pending={toggling}
        errorMessage={confirmOpen ? errorMessage : null}
        onConfirm={unsave}
        onCancel={() => setConfirmOpen(false)}
      />
    </li>
  )
}

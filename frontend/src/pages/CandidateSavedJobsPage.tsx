import { useRef } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { useSavedJobsQuery } from '../features/savedJobs/queries'
import { SavedJobItem } from '../features/savedJobs/SavedJobItem'
import { SAVED_JOB_TEXT, savedCountText } from '../features/savedJobs/savedJobTexts'

// Tran 100 (R-L1) - backend chot; trang chi hien so dem "n/100" (R-V9), n = do dai danh sach API tra ve.
const MAX_ACTIVE_SAVED_JOBS = 100

// FR-U16 - trang "Tin da luu" (/candidate/saved-jobs, UI.md 4c/4d, muc 6). Danh sach theo DUNG thu tu API (saved_at
// DESC, id DESC - R-V1), khong sap lai, khong phan trang (R-V2), khong diem/hang/"phu hop" (R-G2).
export function CandidateSavedJobsPage() {
  const { data: items, isPending, isError, refetch, isFetching } = useSavedJobsQuery()
  const titleRef = useRef<HTMLHeadingElement>(null)

  return (
    <CandidateLayout>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-4 py-8 md:px-6">
        <Card>
          <CardHeader className="flex flex-col gap-1 sm:flex-row sm:items-baseline sm:justify-between">
            <h1 ref={titleRef} tabIndex={-1} className="text-xl font-semibold text-m3-on-surface outline-none">
              {SAVED_JOB_TEXT.pageTitle}
            </h1>
            {items && (
              <p className="text-sm text-m3-on-surface-variant">
                {savedCountText(items.length, MAX_ACTIVE_SAVED_JOBS)}
              </p>
            )}
          </CardHeader>
          <CardContent aria-live="polite">
            {isPending && <SavedJobsSkeleton />}

            {isError && (
              <div className="flex flex-col items-start gap-3">
                <p className="text-sm text-m3-on-surface">{SAVED_JOB_TEXT.listLoadError}</p>
                <Button type="button" variant="outline" onClick={() => void refetch()} disabled={isFetching}>
                  {SAVED_JOB_TEXT.retry}
                </Button>
              </div>
            )}

            {items && items.length === 0 && (
              <div className="flex flex-col items-start gap-2">
                <p className="text-sm text-m3-on-surface-variant">{SAVED_JOB_TEXT.empty}</p>
                <Link to="/candidate" className="text-sm font-medium text-m3-primary hover:underline">
                  {SAVED_JOB_TEXT.findJobs}
                </Link>
              </div>
            )}

            {items && items.length > 0 && (
              <ul>
                {items.map((item) => (
                  <SavedJobItem key={item.jobId} item={item} onRemoved={() => titleRef.current?.focus()} />
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>
    </CandidateLayout>
  )
}

// UI.md muc 6 - skeleton xam 3 hang (logo + 3 dong), khong spinner toan trang.
function SavedJobsSkeleton() {
  return (
    <div className="flex flex-col gap-6" aria-hidden="true">
      {[0, 1, 2].map((key) => (
        <div key={key} className="flex gap-4">
          <div className="h-12 w-12 shrink-0 animate-pulse rounded-(--radius-badge) bg-m3-surface-container sm:h-16 sm:w-16" />
          <div className="flex flex-1 flex-col gap-2">
            <div className="h-5 w-2/3 animate-pulse rounded bg-m3-surface-container" />
            <div className="h-4 w-1/3 animate-pulse rounded bg-m3-surface-container" />
            <div className="h-4 w-1/2 animate-pulse rounded bg-m3-surface-container" />
          </div>
        </div>
      ))}
    </div>
  )
}

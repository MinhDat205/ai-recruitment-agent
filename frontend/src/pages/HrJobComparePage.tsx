import { isAxiosError } from 'axios'
import { AlertCircle, ArrowLeft, RotateCw } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { HrLayout } from '../components/layout/HrLayout'
import { COMPARE_ERROR_TEXT, COMPARE_PAGE_TEXT } from '../features/comparison/comparisonLabels'
import { useApplicationComparisonQuery } from '../features/comparison/queries'
import type { ApplicationComparisonResponse } from '../features/comparison/types'
import { extractErrorMessage } from '../lib/httpError'

// FR-H14 - trang so sanh /hr/jobs/:id/compare?ids=... (trong nhom ProtectedRoute(HR) + RequireCompany, HrLayout; muc
// dieu huong "Tin tuyen dung" sang vi duong dan bat dau bang /hr/jobs/). Chi goi DUNG mot endpoint C1 (R-B1). ids gui
// NGUYEN VAN len backend (R-S9): khong bo trung, khong cat, khong sap - backend la chot chan va tra cau R-S8.
// Dot 4: KHUNG trang (dau trang, cac trang thai muc 6); than bang so sanh lam o dot 5.

type ComparisonErrorKind = 'selection' | 'notScored' | 'notFound' | 'companyMissing' | 'network'

function classifyError(error: unknown): ComparisonErrorKind {
  if (!isAxiosError(error) || error.response === undefined) {
    return 'network'
  }
  const status = error.response.status
  const code = (error.response.data as { error?: unknown } | undefined)?.error
  if (status === 400 && code === 'INVALID_COMPARE_SELECTION') {
    return 'selection'
  }
  if (status === 409 && code === 'COMPARE_APPLICATION_NOT_SCORED') {
    return 'notScored'
  }
  if (status === 404 && code === 'COMPANY_NOT_FOUND') {
    return 'companyMissing'
  }
  // 403 (Job cong ty khac) va 404 (JOB_NOT_FOUND) CUNG mot thong diep - khong lo Job co ton tai (UI_GUIDE muc 5).
  // 400 khac ma (vd jobId tren URL khong phai UUID) cung coi la khong tim thay tin.
  if (status === 400 || status === 403 || status === 404) {
    return 'notFound'
  }
  return 'network'
}

export function HrJobComparePage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()

  if (!id) {
    return null
  }

  // null khi URL khong co tham so ids -> goi C1 khong kem ids, backend tra 400 R-S8.
  const idsParam = searchParams.get('ids')

  return (
    <HrLayout title={COMPARE_PAGE_TEXT.pageTitle}>
      <ComparePageContent jobId={id} idsParam={idsParam} />
    </HrLayout>
  )
}

function ComparePageContent({ jobId, idsParam }: { jobId: string; idsParam: string | null }) {
  const comparisonQuery = useApplicationComparisonQuery(jobId, idsParam)
  const backToListHref = `/hr/jobs/${jobId}/edit?tab=applications`
  const data = comparisonQuery.data

  return (
    <div className="mx-auto flex w-full max-w-[1200px] flex-col gap-4">
      <Link
        to={backToListHref}
        className="inline-flex min-h-12 items-center gap-1.5 self-start text-m3-label-lg text-m3-on-primary-container hover:underline sm:min-h-0"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        {COMPARE_PAGE_TEXT.backToList}
      </Link>

      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-m3-headline-sm text-m3-on-surface">{COMPARE_PAGE_TEXT.pageTitle}</h1>
          {data && (
            <p className="text-m3-body-md text-m3-on-surface">
              {COMPARE_PAGE_TEXT.subtitle(data.jobTitle, data.recruitmentCycle, data.columns.length)}
            </p>
          )}
          {data && <p className="text-m3-body-md text-m3-on-surface">{COMPARE_PAGE_TEXT.decisionNote}</p>}
        </div>
        {data && (
          <Button
            type="button"
            variant="outline"
            className="min-h-12 w-full md:min-h-0 md:w-auto"
            disabled={comparisonQuery.isFetching}
            onClick={() => comparisonQuery.refetch()}
          >
            <RotateCw className="h-4 w-4" aria-hidden="true" />
            {COMPARE_PAGE_TEXT.reload}
          </Button>
        )}
      </div>

      {comparisonQuery.isLoading && <ComparisonSkeleton />}

      {/* Chi coi la loi khi CHUA co du lieu - lan "Tai lai" loi thi giu bang dang hien (cung cach trang ho so don). */}
      {!comparisonQuery.isLoading && !data && (
        <ComparisonError
          error={comparisonQuery.error}
          backToListHref={backToListHref}
          onRetry={() => comparisonQuery.refetch()}
        />
      )}

      {data && <ComparisonPlaceholder data={data} />}
    </div>
  )
}

// Skeleton xam: mot hang dau cot + 6 hang (UI.md muc 6), khong spinner toan trang.
function ComparisonSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-busy="true" aria-hidden="true">
      <div className="h-24 animate-pulse rounded-(--radius-card) bg-m3-surface-container-high motion-reduce:animate-none" />
      {Array.from({ length: 6 }, (_, index) => (
        <div
          key={index}
          className="h-12 animate-pulse rounded-(--radius-card) bg-m3-surface-container-high motion-reduce:animate-none"
        />
      ))}
    </div>
  )
}

// Cau loi nam tren nen trang: chu on-surface, tin hieu loi bang icon m3-error (UI_GUIDE muc 6). Cau trong role="alert".
function ComparisonError({
  error,
  backToListHref,
  onRetry,
}: {
  error: unknown
  backToListHref: string
  onRetry: () => void
}) {
  const kind = classifyError(error)

  let message: string
  let hint: string | null = null
  let action: { to: string; label: string } | null = null
  if (kind === 'selection') {
    message = extractErrorMessage(error, COMPARE_ERROR_TEXT.loadFailed)
    action = { to: backToListHref, label: COMPARE_PAGE_TEXT.backToList }
  } else if (kind === 'notScored') {
    message = extractErrorMessage(error, COMPARE_ERROR_TEXT.loadFailed)
    hint = COMPARE_ERROR_TEXT.notScoredHint
    action = { to: backToListHref, label: COMPARE_PAGE_TEXT.backToList }
  } else if (kind === 'companyMissing') {
    // Cau backend + lien ket tao ho so cong ty (tien le AiDraftInputPanel). Route da boc RequireCompany nen thuc te
    // hiem khi toi day.
    message = extractErrorMessage(error, COMPARE_ERROR_TEXT.loadFailed)
    action = { to: '/hr/company', label: COMPARE_ERROR_TEXT.createCompany }
  } else if (kind === 'notFound') {
    message = COMPARE_ERROR_TEXT.notFound
    action = { to: '/hr/jobs', label: COMPARE_ERROR_TEXT.backToJobs }
  } else {
    message = COMPARE_ERROR_TEXT.loadFailed
  }

  return (
    <div className="flex flex-col items-start gap-3">
      <div role="alert" className="flex items-start gap-2 text-m3-body-md text-m3-on-surface">
        <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
        <div className="flex flex-col gap-1">
          <p>{message}</p>
          {hint && <p>{hint}</p>}
        </div>
      </div>
      {action && (
        <Link
          to={action.to}
          className="inline-flex min-h-12 items-center text-m3-label-lg text-m3-on-primary-container hover:underline sm:min-h-0"
        >
          {action.label}
        </Link>
      )}
      {kind === 'network' && (
        <Button type="button" variant="outline" className="min-h-12 sm:min-h-0" onClick={onRetry}>
          {COMPARE_ERROR_TEXT.retry}
        </Button>
      )}
    </div>
  )
}

// Dot 4: khoi giu cho trung tinh, chi ghi so cot. Dot 5 thay bang bang so sanh (ComparisonTable/ComparisonStacked).
function ComparisonPlaceholder({ data }: { data: ApplicationComparisonResponse }) {
  return (
    <div className="rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface px-4 py-6 text-m3-body-md text-m3-on-surface">
      {data.columns.length} cột
    </div>
  )
}

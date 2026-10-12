import { useId } from 'react'
import { AlertCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatDateTimeVi } from '../../lib/date'
import { CANDIDATE_ANSWERS_TEXT } from '../candidateScreening/candidateScreeningLabels'
import { useMyApplicationScreeningQuery } from '../candidateScreening/queries'
import { ScreeningAnswerList } from '../screening/ScreeningAnswerList'

const CANDIDATE_ANSWER_TEXTS = {
  yes: CANDIDATE_ANSWERS_TEXT.yes,
  no: CANDIDATE_ANSWERS_TEXT.no,
  noAnswer: CANDIDATE_ANSWERS_TEXT.noAnswer,
}

// Khoi "Câu trả lời sàng lọc" (FR-U09 R-D2, UI.md 4e, 4f) - goi F3, chi doc ban chup cua CHINH don minh. Trang thai doc tu
// `state` cua backend. NO_QUESTIONS va NOT_CAPTURED: KHONG render khoi (Q20 - ngoai le co chu dich voi UI_GUIDE muc 4,
// tien le R-T5 cua FR-U08): NOT_CAPTURED chi nghia la don nop truoc FR-U09, moi cau thong bao deu gay hieu lam. Khong to
// mau, khong danh dau theo cau tra loi (R-N3).
export function ScreeningAnswersSection({ applicationId }: { applicationId: string }) {
  const headingId = useId()
  const { data, isLoading, isError, refetch } = useMyApplicationScreeningQuery(applicationId)

  if (data && (data.state !== 'CAPTURED' || !data.capturedAt)) {
    return null
  }

  let content
  if (isLoading) {
    content = (
      <div className="flex flex-col gap-4" aria-hidden="true">
        {[0, 1, 2].map((i) => (
          <div key={i} className="flex flex-col gap-2">
            <div className="h-5 w-2/3 animate-pulse rounded-m3-xs bg-m3-surface-container" />
            <div className="h-4 w-1/3 animate-pulse rounded-m3-xs bg-m3-surface-container" />
            <div className="h-4 w-1/2 animate-pulse rounded-m3-xs bg-m3-surface-container" />
          </div>
        ))}
      </div>
    )
  } else if (isError || !data || !data.capturedAt) {
    content = (
      <div className="flex flex-col items-start gap-2">
        <p role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          {CANDIDATE_ANSWERS_TEXT.loadError}
        </p>
        <Button type="button" variant="outline" size="sm" onClick={() => void refetch()}>
          {CANDIDATE_ANSWERS_TEXT.retry}
        </Button>
      </div>
    )
  } else {
    content = (
      <>
        <p className="text-sm break-words text-m3-on-surface-variant">
          {CANDIDATE_ANSWERS_TEXT.caption(formatDateTimeVi(data.capturedAt))}
        </p>
        <ScreeningAnswerList items={data.items} texts={CANDIDATE_ANSWER_TEXTS} />
      </>
    )
  }

  return (
    <section aria-labelledby={headingId} className="flex min-w-0 flex-col gap-3 py-6 first:pt-0 last:pb-0">
      <h2 id={headingId} className="text-m3-title-md text-m3-on-surface">
        {CANDIDATE_ANSWERS_TEXT.title}
      </h2>
      <div aria-live="polite" className="flex min-w-0 flex-col gap-3">
        {content}
      </div>
    </section>
  )
}

import { formatDateTimeVi } from '../../lib/date'
import { useApplicationScreeningQuery } from '../screening/queries'
import { ScreeningAnswerList } from '../screening/ScreeningAnswerList'
import { SCREENING_ANSWER_TEXT } from '../screening/screeningLabels'
import { TabLoadError } from './ScoreSection'

// Chu phia HR cho danh sach dung chung (FR-U09 R-C5) - giu nguyen van UI.md muc 7 cua FR-H10.
const HR_ANSWER_TEXTS = {
  yes: SCREENING_ANSWER_TEXT.yes,
  no: SCREENING_ANSWER_TEXT.no,
  noAnswer: SCREENING_ANSWER_TEXT.noAnswer,
}

// Tab "Sàng lọc" (FR-H10 S6, UI.md 4d, 4e, 4g) - CHI DOC ban chup cau hoi + cau tra loi luc nop don. Khong nut sua/xoa,
// khong loc, khong sap xep, khong diem, khong to mau theo cau tra loi (R-N4, UI.md muc 10). Trang thai doc tu `state`
// cua backend, khong tu suy tu items.length (R-A3). Moi chuoi la van ban thuong, khong render HTML.
// FR-U09 R-C5: phan danh sach chuyen sang ScreeningAnswerList dung chung; giao dien va chu cua tab khong doi.
export function ScreeningTab({ applicationId }: { applicationId: string }) {
  const { data, isLoading, isError, refetch } = useApplicationScreeningQuery(applicationId)

  if (isLoading) {
    return <ScreeningSkeleton />
  }
  if (isError || !data) {
    return <TabLoadError onRetry={() => void refetch()} />
  }

  return (
    <section className="flex min-w-0 flex-col gap-4">
      <h2 className="text-m3-title-md text-m3-on-surface">{SCREENING_ANSWER_TEXT.title}</h2>

      {data.state === 'NOT_CAPTURED' && (
        <p className="text-m3-body-md break-words text-m3-on-surface">{SCREENING_ANSWER_TEXT.notCaptured}</p>
      )}

      {data.state === 'NO_QUESTIONS' && data.capturedAt && (
        <p className="text-m3-body-md break-words text-m3-on-surface">
          {SCREENING_ANSWER_TEXT.noQuestions(formatDateTimeVi(data.capturedAt))}
        </p>
      )}

      {data.state === 'CAPTURED' && data.capturedAt && (
        <>
          <p className="text-sm break-words text-m3-on-surface-variant">
            {SCREENING_ANSWER_TEXT.caption(formatDateTimeVi(data.capturedAt))}
          </p>
          <ScreeningAnswerList items={data.items} texts={HR_ANSWER_TEXTS} />
        </>
      )}
    </section>
  )
}

function ScreeningSkeleton() {
  return (
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
}

import { formatDateTimeVi } from '../../lib/date'
import { useApplicationScreeningQuery } from '../screening/queries'
import {
  SCREENING_ANSWER_TEXT,
  SCREENING_QUESTION_TYPE_LABELS,
  SCREENING_TEXT,
} from '../screening/screeningLabels'
import type { ApplicationScreeningItem } from '../screening/types'
import { TabLoadError } from './ScoreSection'

// Tab "Sàng lọc" (FR-H10 S6, UI.md 4d, 4e, 4g) - CHI DOC ban chup cau hoi + cau tra loi luc nop don. Khong nut sua/xoa,
// khong loc, khong sap xep, khong diem, khong to mau theo cau tra loi (R-N4, UI.md muc 10). Trang thai doc tu `state`
// cua backend, khong tu suy tu items.length (R-A3). Moi chuoi la van ban thuong, khong render HTML.
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
          <ol className="flex flex-col">
            {[...data.items]
              .sort((a, b) => a.displayOrder - b.displayOrder)
              .map((item, index) => (
                <ScreeningAnswerItem key={item.displayOrder} item={item} position={index + 1} />
              ))}
          </ol>
        </>
      )}
    </section>
  )
}

// SINGLE_CHOICE: answerText la chu cua phuong an da chon trong ban chup - chi hien chu do, khong liet ke cac phuong an
// con lai, khong danh dau (UI.md 4d). "Co", "Khong", chu phuong an va cau tra loi ngan cung kieu chu, cung mau (R-N4).
function answerOf(item: ApplicationScreeningItem): string | null {
  if (item.questionType === 'YES_NO') {
    return item.answerYesNo === null ? null : item.answerYesNo ? SCREENING_ANSWER_TEXT.yes : SCREENING_ANSWER_TEXT.no
  }
  return item.answerText
}

function ScreeningAnswerItem({ item, position }: { item: ApplicationScreeningItem; position: number }) {
  const answer = answerOf(item)
  const meta = `${SCREENING_QUESTION_TYPE_LABELS[item.questionType]} · ${
    item.required ? SCREENING_TEXT.required : SCREENING_TEXT.notRequired
  }`
  return (
    <li className="flex min-w-0 gap-1 border-t border-m3-outline-variant py-4 first:border-t-0 first:pt-0">
      <span className="shrink-0 text-m3-title-md text-m3-on-surface">{position}.</span>
      <div className="flex min-w-0 flex-col gap-1">
        <p className="text-m3-title-md break-words text-m3-on-surface">{item.questionText}</p>
        <p className="text-m3-body-sm text-m3-on-surface-variant">{meta}</p>
        {answer === null ? (
          <p className="text-m3-body-md text-m3-on-surface-variant">{SCREENING_ANSWER_TEXT.noAnswer}</p>
        ) : (
          <p className="text-m3-body-md break-words whitespace-pre-wrap text-m3-on-surface">{answer}</p>
        )}
      </div>
    </li>
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

import { useId, useRef, useState, type KeyboardEvent } from 'react'
import { AlertCircle, Check } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { CardContent } from '@/components/ui/card'
import { useHrReconsiderationsQuery } from './queries'
import { HR_RECONSIDERATION_TEXT as TEXT } from './reconsiderationLabels'
import { ReconsiderationCard } from './ReconsiderationCard'
import { useReconsiderationDialogs } from './useReconsiderationDialogs'
import type { HrReconsiderationFilter } from './types'

const FILTERS: HrReconsiderationFilter[] = ['OPEN', 'ALL']
const FILTER_LABELS: Record<HrReconsiderationFilter, string> = { OPEN: TEXT.filterOpen, ALL: TEXT.filterAll }

// Bo chon hai gia tri (Segmented button, UI_GUIDE muc 3) - khuon ToneSegmentedControl: role="radiogroup" + moi nut
// role="radio"/aria-checked, roving tabindex, phim mui ten doi lua chon, lua chon dang chon co icon Check (khong chi mau).
function FilterSegmentedControl({
  value,
  onChange,
}: {
  value: HrReconsiderationFilter
  onChange: (filter: HrReconsiderationFilter) => void
}) {
  const buttonRefs = useRef<Array<HTMLButtonElement | null>>([])

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const step =
      event.key === 'ArrowRight' || event.key === 'ArrowDown'
        ? 1
        : event.key === 'ArrowLeft' || event.key === 'ArrowUp'
          ? -1
          : 0
    if (step === 0) {
      return
    }
    event.preventDefault()
    const next = (index + step + FILTERS.length) % FILTERS.length
    onChange(FILTERS[next])
    buttonRefs.current[next]?.focus()
  }

  return (
    <div role="radiogroup" aria-label={TEXT.filterLabel} className="grid w-full grid-cols-2 sm:inline-grid sm:w-auto">
      {FILTERS.map((filter, index) => {
        const selected = filter === value
        return (
          <button
            key={filter}
            ref={(element) => {
              buttonRefs.current[index] = element
            }}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(filter)}
            onKeyDown={(event) => handleKeyDown(event, index)}
            className={`inline-flex min-h-12 items-center justify-center gap-1.5 border border-m3-outline px-3 py-2 text-sm break-words sm:min-h-10 ${
              index === 0 ? 'rounded-l-m3-sm' : '-ml-px rounded-r-m3-sm'
            } ${
              selected
                ? 'bg-m3-primary-container text-m3-on-primary-container'
                : 'bg-m3-surface text-m3-on-surface hover:bg-m3-primary/8'
            }`}
          >
            {selected && <Check className="h-4 w-4 shrink-0" aria-hidden="true" />}
            {FILTER_LABELS[filter]}
          </button>
        )
      })}
    </div>
  )
}

// Tab "Yêu cầu xem xét lại" cua trang sua tin (FR-C09 UI.md 4e, muc 6). Mac dinh "Đang chờ" - cung khoa query (jobId,
// 'OPEN') voi so tren nhan tab (mot request). Moi yeu cau mot the (van ban dai, khong ep vao bang). Rong: chi cau, khong
// nut. Loi tai: icon + cau + "Thử lại". Le ngang theo cac tab khac (CardContent pt-4, khuon ScreeningQuestionsTab).
export function ReconsiderationTab({ jobId }: { jobId: string }) {
  const headingId = useId()
  const headingRef = useRef<HTMLHeadingElement>(null)
  const [filter, setFilter] = useState<HrReconsiderationFilter>('OPEN')
  const [announced, setAnnounced] = useState(false)
  const query = useHrReconsiderationsQuery(jobId, filter)
  // Giu tu choi xong: bo loc "Đang chờ" lam the roi danh sach - dua focus ve tieu de tab (UI.md 4g, muc 9).
  const { openInvite, openDecline, dialogs } = useReconsiderationDialogs(jobId, () => {
    setAnnounced(true)
    headingRef.current?.focus()
  })

  const items = query.data?.items ?? []

  return (
    <CardContent className="pt-4">
      <section aria-labelledby={headingId} className="flex min-w-0 flex-col gap-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex min-w-0 flex-col gap-1">
            <h2
              id={headingId}
              ref={headingRef}
              tabIndex={-1}
              className="text-m3-title-md text-m3-on-surface outline-none focus-visible:ring-2 focus-visible:ring-m3-primary"
            >
              {TEXT.tabTitle}
            </h2>
            <p className="text-sm break-words text-m3-on-surface-variant">{TEXT.tabDescription}</p>
          </div>
          <FilterSegmentedControl value={filter} onChange={setFilter} />
        </div>

        <p aria-live="polite" className="sr-only">
          {announced ? TEXT.declinedAnnouncement : ''}
        </p>

        {query.isPending && (
          <div className="flex flex-col gap-3" aria-hidden="true">
            {[0, 1, 2].map((index) => (
              <div key={index} className="h-32 animate-pulse rounded-(--radius-card) bg-m3-surface-container" />
            ))}
          </div>
        )}

        {query.isError && (
          <div className="flex flex-col items-start gap-3">
            <p role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
              {TEXT.loadError}
            </p>
            <Button type="button" variant="outline" onClick={() => void query.refetch()}>
              {TEXT.retry}
            </Button>
          </div>
        )}

        {query.isSuccess && items.length === 0 && (
          <p className="text-sm text-m3-on-surface-variant">{filter === 'OPEN' ? TEXT.emptyOpen : TEXT.emptyAll}</p>
        )}

        {query.isSuccess && items.length > 0 && (
          <div className="flex flex-col gap-3">
            {items.map((item) => (
              <ReconsiderationCard
                key={item.applicationId}
                item={item}
                variant="tab"
                onInvite={openInvite}
                onDecline={openDecline}
              />
            ))}
          </div>
        )}
        {dialogs}
      </section>
    </CardContent>
  )
}

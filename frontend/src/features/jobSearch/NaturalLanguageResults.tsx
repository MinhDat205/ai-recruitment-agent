import { SlidersHorizontal } from 'lucide-react'
import { Fragment, useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { JobCard, type JobCardFilterContext } from '../jobs/JobCard'
import { Pagination } from '../jobs/Pagination'
import { JOB_SEARCH_PAGE_SIZE, JOB_SEARCH_TEXT, nlResultCount } from './jobSearchLabels'
import type { JobSearchInterpretation, JobSearchItem, JobSearchRanking } from './types'

interface NaturalLanguageResultsProps {
  interpretation: JobSearchInterpretation
  items: JobSearchItem[]
  ranking: JobSearchRanking
  onEditConditions: () => void
}

// FR-U13 UI.md muc 4a, 5a, 6, 9 - danh sach ket qua (toi da 50) chia 10/trang o TRINH DUYET: doi trang khong goi API.
// Cha dat key theo ma luot gui nen moi ket qua moi la mot lan mount moi -> trang tu ve 1 va focus chuyen toi tieu de.
export function NaturalLanguageResults({ interpretation, items, ranking, onEditConditions }: NaturalLanguageResultsProps) {
  const [page, setPage] = useState(0)

  // L45 - cung ba bieu thuc JobBoard dung filterContext tu bo loc U07 (categoryCode/locationCode/salaryMin-salaryMax),
  // ap len cac dieu kien da hieu: the viec hien nhan "Chua chuan hoa"/chu thich ngoai te nhu danh sach U07.
  const filterContext: JobCardFilterContext = {
    isCategoryFilterActive: interpretation.industryCodes.length > 0,
    isLocationFilterActive: interpretation.locationCodes.length > 0,
    isSalaryFilterActive: interpretation.salaryMin != null || interpretation.salaryMax != null,
  }
  const titleRef = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    titleRef.current?.focus()
  }, [])

  const totalPages = Math.ceil(items.length / JOB_SEARCH_PAGE_SIZE)
  const start = page * JOB_SEARCH_PAGE_SIZE
  const pageItems = items.slice(start, start + JOB_SEARCH_PAGE_SIZE)

  function handlePageChange(next: number) {
    setPage(next)
    // Doi trang: dua nguoi dung ve tieu de ket qua (focus cung cuon toi do).
    titleRef.current?.focus()
  }

  // R-K4 - cau ngan cach dung truoc viec rankedByDescription=false dau tien cua trang khi viec truoc no (trong trang)
  // la true, hoac o dau trang neu trang bat dau bang viec false. Nhanh NEWEST khong co cau ngan cach.
  function showDividerBefore(index: number): boolean {
    if (ranking !== 'DESCRIPTION' || pageItems[index].rankedByDescription) {
      return false
    }
    return index === 0 || pageItems[index - 1].rankedByDescription
  }

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-1 sm:flex-row sm:items-baseline sm:justify-between sm:gap-3">
        <h2 ref={titleRef} tabIndex={-1} className="text-xl font-semibold text-m3-on-surface outline-none">
          {JOB_SEARCH_TEXT.resultsTitle}
        </h2>
        <p role="status" className="text-sm text-m3-on-surface">
          {items.length > 0 ? nlResultCount(items.length) : JOB_SEARCH_TEXT.nlEmptyResults}
        </p>
      </div>

      {items.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-12 text-center">
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="min-h-12 w-full sm:min-h-10 sm:w-auto"
            onClick={onEditConditions}
          >
            <SlidersHorizontal aria-hidden="true" />
            {JOB_SEARCH_TEXT.editConditions}
          </Button>
        </div>
      ) : (
        <div>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            {pageItems.map((item, index) => (
              <Fragment key={item.job.id}>
                {showDividerBefore(index) && (
                  <p className="col-span-full border-t border-m3-outline-variant pt-3 text-sm text-m3-on-surface">
                    {JOB_SEARCH_TEXT.nlUnrankedDivider}
                  </p>
                )}
                <JobCard
                  job={item.job}
                  filterContext={filterContext}
                  matchedConditions={item.matchedConditions}
                  matchedConditionsLabel={JOB_SEARCH_TEXT.matchedPrefix}
                />
              </Fragment>
            ))}
          </div>
          <Pagination page={page} totalPages={totalPages} onPageChange={handlePageChange} />
        </div>
      )}
    </section>
  )
}

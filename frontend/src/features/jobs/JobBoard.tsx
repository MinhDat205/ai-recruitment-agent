import { Info } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { buildFilterParams } from '../jobSearch/buildFilterParams'
import { JOB_SEARCH_TEXT } from '../jobSearch/jobSearchLabels'
import { NaturalLanguageSearch } from '../jobSearch/NaturalLanguageSearch'
import { SearchModeSwitch, type SearchMode } from '../jobSearch/SearchModeSwitch'
import type { JobSearchCondition, JobSearchInterpretation } from '../jobSearch/types'
import { JobFilterBar } from './JobFilterBar'
import { JobList } from './JobList'
import { RecommendedJobs } from './RecommendedJobs'
import { useJobsQuery } from './queries'
import { useJobFilters } from './useJobFilters'

interface JobBoardProps {
  // FR-U15 R-L3/R-L4 - true o /candidate (CandidateJobListPage truyen vao), false (mac dinh,
  // khong doi) o "/" (PublicJobListPage khong truyen) - trang cong khai/khach khong co khoi goi y.
  showRecommendations?: boolean
  // FR-U13 UI.md muc 5b - true chi o /candidate: them bo chon che do "Tim bang mo ta". false (mac dinh, "/" khong
  // truyen): hanh vi y het truoc FR-U13.
  naturalLanguageSearch?: boolean
}

// Noi dung "Viec lam" dung chung cho "/" (khach/ung vien chua dang nhap) va "/candidate" (ung vien
// da dang nhap) - R-L3: hai trang chi con khac o layout boc ngoai (PublicLayout/CandidateLayout),
// toan bo thanh loc + so ket qua + danh sach + phan trang nam trong 1 component nay. useJobsQuery
// goi DUNG MOT lan o day (truoc day moi trang goi rieng + JobList tu goi lai = 2 lan cho cung 1
// apiParams) - JobList gio chi nhan data/trang thai qua props, khong tu fetch.
export function JobBoard({ showRecommendations = false, naturalLanguageSearch = false }: JobBoardProps) {
  const { filters, apiParams, setPage, canQueryJobs, hasActiveFilters, clearFilters } = useJobFilters()
  // FR-U13 Q-T6 - che do va cau mo ta chi o state cua trang, KHONG len URL: tai lai trang -> ve che do tu khoa.
  const [mode, setMode] = useState<SearchMode>('keyword')
  const [descriptionDraft, setDescriptionDraft] = useState('')
  // 0 = khong hien cau switchedNotice; tang moi lan sua/xoa the de focus lai cau bao.
  const [switchedNoticeVersion, setSwitchedNoticeVersion] = useState(0)
  const switchedNoticeRef = useRef<HTMLParagraphElement>(null)
  const [, setSearchParams] = useSearchParams()
  // Che do mo ta khong goi /public/jobs thua (UI.md muc 5b).
  const jobsQuery = useJobsQuery(apiParams, { enabled: canQueryJobs && mode === 'keyword' })

  useEffect(() => {
    if (switchedNoticeVersion > 0) {
      switchedNoticeRef.current?.focus()
    }
  }, [switchedNoticeVersion])

  // FR-U15 R-L3 - khoi goi y CHI hien khi: showRecommendations=true, dang o trang 1, chua co tu
  // khoa, khong co bo loc nao dang ap. Khi dieu kien nay sai, RecommendedJobs KHONG duoc render -
  // khong goi API goi y (tranh goi thua khong can thiet khi khong hien).
  const showRecommendedBlock =
    showRecommendations && filters.page === 0 && filters.keyword === '' && !hasActiveFilters

  function handleModeChange(next: SearchMode) {
    setMode(next)
    if (next === 'description') {
      setSwitchedNoticeVersion(0)
    }
  }

  // FR-U13 Q-T5 - sua/xoa the: MOT lan setSearchParams (mot muc lich su) voi bo loc U07, ve che do tu khoa, khong goi
  // lai AI. Ket qua mo ta bi bo (NaturalLanguageSearch unmount).
  function handleEditConditions(interpretation: JobSearchInterpretation, removed?: JobSearchCondition) {
    setSearchParams(buildFilterParams(interpretation, removed))
    setMode('keyword')
    setSwitchedNoticeVersion((version) => version + 1)
  }

  return (
    <div className="mx-auto max-w-[1200px] px-4 py-8 md:px-6">
      {showRecommendedBlock && mode === 'keyword' && (
        <div className="mb-8">
          <RecommendedJobs />
        </div>
      )}
      {/* Bo chon o CUNG vi tri trong cay o ca hai che do (khong remount -> radio giu focus khi doi bang phim mui ten).
          Che do mo ta: khung nay la nua tren cua thanh tim kiem, NaturalLanguageSearch ve nua duoi. */}
      {naturalLanguageSearch && (
        <div
          className={cn(
            mode === 'description'
              ? 'rounded-t-m3-sm border border-b-0 border-m3-outline-variant bg-m3-surface px-4 pt-4'
              : 'mb-3',
          )}
        >
          <SearchModeSwitch value={mode} onChange={handleModeChange} />
        </div>
      )}
      {mode === 'description' ? (
        <NaturalLanguageSearch
          query={descriptionDraft}
          onQueryChange={setDescriptionDraft}
          onEditConditions={handleEditConditions}
        />
      ) : (
        <>
          <JobFilterBar totalElements={jobsQuery.data?.totalElements} />
          {switchedNoticeVersion > 0 && (
            <p
              ref={switchedNoticeRef}
              tabIndex={-1}
              role="status"
              className="mt-3 flex items-start gap-1.5 text-sm text-m3-on-surface outline-none"
            >
              <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              {JOB_SEARCH_TEXT.switchedNotice}
            </p>
          )}
          <h2 className="mt-6 mb-4 text-xl font-semibold text-m3-on-surface">Việc làm đang tuyển</h2>
          <JobList
            data={jobsQuery.data}
            isPending={jobsQuery.isPending}
            isError={jobsQuery.isError}
            onRetry={() => jobsQuery.refetch()}
            onPageChange={setPage}
            hasActiveFilters={hasActiveFilters}
            onClearFilters={clearFilters}
            filterContext={{
              // FR-U15 dot 6 doi categoryCode/locationCode sang string[] - sua lai dieu kien cho khop
              // (truoc la "!= null", luon dung voi kieu mang khong bao gio null, thanh loi am tham).
              isCategoryFilterActive: filters.categoryCode.length > 0,
              isLocationFilterActive: filters.locationCode.length > 0,
              // R-S5: chu thich ngoai te giai thich vi sao tin "lech" khoi khoang luong dang loc van
              // hien - chi co nghia khi THAT SU co khoang loc (salaryMin/salaryMax). hideUnlisted
              // dung mot minh (R-S4, khong kem salaryMin/salaryMax) khong so khoang nao ca nen khong
              // tinh vao day.
              isSalaryFilterActive: filters.salaryMin != null || filters.salaryMax != null,
            }}
          />
        </>
      )}
    </div>
  )
}

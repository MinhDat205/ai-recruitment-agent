import { JobCard, type JobCardFilterContext } from './JobCard'
import { JobCardSkeleton } from './JobCardSkeleton'
import { Pagination } from './Pagination'
import type { JobSummary, PageResponse } from './types'

interface JobListProps {
  data: PageResponse<JobSummary> | undefined
  // true khi dang hoan cho danh muc tai xong (useJobFilters.canQueryJobs=false) HOAC dang fetch
  // lan dau - ca hai deu chua co data/loi (react-query status "pending"), hien skeleton nhu nhau.
  isPending: boolean
  isError: boolean
  onRetry: () => void
  onPageChange: (page: number) => void
  // UI.md muc 6: nut "Xoa bo loc" o trang thai rong CHI hien khi dang co loc.
  hasActiveFilters: boolean
  onClearFilters: () => void
  filterContext: JobCardFilterContext
}

// Thuan hien thi - KHONG tu goi useJobsQuery (R-L3): du lieu/trang thai do component cha
// (JobBoard) doc mot lan va truyen xuong, tranh goi API 2 lan cho cung 1 man hinh.
export function JobList({
  data,
  isPending,
  isError,
  onRetry,
  onPageChange,
  hasActiveFilters,
  onClearFilters,
  filterContext,
}: JobListProps) {
  if (isPending) {
    return (
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {Array.from({ length: 6 }).map((_, index) => (
          <JobCardSkeleton key={index} />
        ))}
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center">
        <p className="text-sm text-m3-on-surface">Không tải được danh sách việc làm.</p>
        <button
          type="button"
          onClick={onRetry}
          className="h-10 rounded-md border border-m3-primary px-5 text-sm font-medium text-m3-on-primary-container"
        >
          Thử lại
        </button>
      </div>
    )
  }

  if (!data || data.items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center">
        <p className="text-sm text-m3-on-surface">Không tìm thấy việc làm phù hợp với bộ lọc hiện tại.</p>
        {hasActiveFilters && (
          <button
            type="button"
            onClick={onClearFilters}
            className="h-10 rounded-md border border-m3-primary px-5 text-sm font-medium text-m3-on-primary-container"
          >
            Xoá bộ lọc
          </button>
        )}
      </div>
    )
  }

  return (
    <div>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {data.items.map((job) => (
          <JobCard key={job.id} job={job} filterContext={filterContext} />
        ))}
      </div>
      <Pagination page={data.page} totalPages={data.totalPages} onPageChange={onPageChange} />
    </div>
  )
}

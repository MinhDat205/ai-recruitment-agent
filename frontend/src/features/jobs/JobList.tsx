import { useJobsQuery } from './queries'
import { JobCard } from './JobCard'
import { JobCardSkeleton } from './JobCardSkeleton'
import { Pagination } from './Pagination'
import type { JobSearchParams } from './types'

interface JobListProps {
  params: JobSearchParams
  onPageChange: (page: number) => void
  // FR-U07: false khi dang hoan truy van cho danh muc tai xong (useJobFilters.canQueryJobs) - mac
  // dinh true cho moi noi dung khong lien quan bo loc danh muc.
  enabled?: boolean
}

export function JobList({ params, onPageChange, enabled = true }: JobListProps) {
  // isPending (khong dung isLoading): dung ca khi query bi enabled=false va chua tung fetch (status
  // "pending", chua co data/loi) - dung truong hop nay de hien skeleton trong luc hoan cho danh muc,
  // khong de lot xuong nhanh "khong tim thay" (data con undefined nhung khong phai vi rong).
  const { data, isPending, isError, refetch } = useJobsQuery(params, { enabled })

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
        <p className="text-sm text-ink-muted">Không tải được danh sách việc làm.</p>
        <button
          type="button"
          onClick={() => refetch()}
          className="h-10 rounded-md border border-brand px-5 text-sm font-medium text-brand"
        >
          Thử lại
        </button>
      </div>
    )
  }

  if (!data || data.items.length === 0) {
    return (
      <div className="py-16 text-center text-sm text-ink-muted">
        Không tìm thấy tin tuyển dụng phù hợp.
      </div>
    )
  }

  return (
    <div>
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {data.items.map((job) => (
          <JobCard key={job.id} job={job} />
        ))}
      </div>
      <Pagination page={data.page} totalPages={data.totalPages} onPageChange={onPageChange} />
    </div>
  )
}

import { useJobsQuery } from './queries'
import { JobCard } from './JobCard'
import { JobCardSkeleton } from './JobCardSkeleton'
import { Pagination } from './Pagination'
import type { JobSearchParams } from './types'

interface JobListProps {
  params: JobSearchParams
  onPageChange: (page: number) => void
}

export function JobList({ params, onPageChange }: JobListProps) {
  const { data, isLoading, isError, refetch } = useJobsQuery(params)

  if (isLoading) {
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

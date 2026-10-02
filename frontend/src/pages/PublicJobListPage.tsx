import { PublicLayout } from '../components/layout/PublicLayout'
import { JobFilterBar } from '../features/jobs/JobFilterBar'
import { JobList } from '../features/jobs/JobList'
import { useJobFilters } from '../features/jobs/useJobFilters'
import { useJobsQuery } from '../features/jobs/queries'

export function PublicJobListPage() {
  const { apiParams, setPage, canQueryJobs } = useJobFilters()
  // Cung query key voi JobList ben duoi (cung apiParams) - React Query dung chung 1 cache entry,
  // khong goi API 2 lan; goi o day chi de lay totalElements hien trong JobFilterBar. enabled phai
  // khop voi JobList ben duoi, neu khong se co 2 trang thai khac nhau cho cung 1 cache entry.
  const jobsQuery = useJobsQuery(apiParams, { enabled: canQueryJobs })

  return (
    <PublicLayout>
      <div className="mx-auto max-w-[1200px] px-4 py-8 md:px-6">
        <JobFilterBar totalElements={jobsQuery.data?.totalElements} />
        <h2 className="mt-6 mb-4 text-xl font-semibold text-ink">Việc làm đang tuyển</h2>
        <JobList params={apiParams} onPageChange={setPage} enabled={canQueryJobs} />
      </div>
    </PublicLayout>
  )
}

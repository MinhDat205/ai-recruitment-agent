import { CandidateLayout } from '../components/layout/CandidateLayout'
import { JobFilterBar } from '../features/jobs/JobFilterBar'
import { JobList } from '../features/jobs/JobList'
import { useJobFilters } from '../features/jobs/useJobFilters'
import { useJobsQuery } from '../features/jobs/queries'

// Noi dung "Viec lam" cho candidate da dang nhap - mirror PublicJobListPage.tsx (cung
// JobFilterBar/JobList/useJobFilters) nhung boc CandidateLayout thay vi PublicLayout de giu
// header/nav tai khoan. Khong tach hook dung chung voi PublicJobListPage o muc nay - viec gop 2
// trang thanh 1 component chung la pham vi dot 4 (R-L3).
export function CandidateJobListPage() {
  const { apiParams, setPage, canQueryJobs } = useJobFilters()
  const jobsQuery = useJobsQuery(apiParams, { enabled: canQueryJobs })

  return (
    <CandidateLayout>
      <div className="mx-auto max-w-[1200px] px-4 py-8 md:px-6">
        <JobFilterBar totalElements={jobsQuery.data?.totalElements} />
        <h2 className="mt-6 mb-4 text-xl font-semibold text-ink">Việc làm đang tuyển</h2>
        <JobList params={apiParams} onPageChange={setPage} enabled={canQueryJobs} />
      </div>
    </CandidateLayout>
  )
}

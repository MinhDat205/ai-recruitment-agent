import { useSearchParams } from 'react-router-dom'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { HeroSearch } from '../features/jobs/HeroSearch'
import { JobList } from '../features/jobs/JobList'

// Noi dung "Viec lam" cho candidate da dang nhap - mirror PublicJobListPage.tsx (cung
// HeroSearch/JobList, cung logic tham so tim kiem) nhung boc CandidateLayout thay vi PublicLayout
// de giu header/nav tai khoan. Khong tach hook dung chung voi PublicJobListPage - trung lap ~15
// dong, chua can abstraction cho 2 lan dung (CLAUDE.md).
export function CandidateJobListPage() {
  const [searchParams, setSearchParams] = useSearchParams()

  const page = Number(searchParams.get('page') ?? '0')
  const params = {
    keyword: searchParams.get('keyword') ?? undefined,
    location: searchParams.get('location') ?? undefined,
    category: searchParams.get('category') ?? undefined,
    page: Number.isNaN(page) ? 0 : page,
    size: 10,
  }

  function handlePageChange(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    setSearchParams(next)
  }

  return (
    <CandidateLayout>
      <HeroSearch />
      <div className="mx-auto max-w-[1200px] px-4 py-8 md:px-6">
        <h2 className="mb-4 text-xl font-semibold text-ink">Việc làm đang tuyển</h2>
        <JobList params={params} onPageChange={handlePageChange} />
      </div>
    </CandidateLayout>
  )
}

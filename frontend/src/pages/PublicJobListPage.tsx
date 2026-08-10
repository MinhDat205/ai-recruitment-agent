import { useSearchParams } from 'react-router-dom'
import { PublicLayout } from '../components/layout/PublicLayout'
import { HeroSearch } from '../features/jobs/HeroSearch'
import { JobList } from '../features/jobs/JobList'

export function PublicJobListPage() {
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
    <PublicLayout>
      <HeroSearch />
      <div className="mx-auto max-w-[1200px] px-4 py-8 md:px-6">
        <h2 className="mb-4 text-xl font-semibold text-ink">Việc làm đang tuyển</h2>
        <JobList params={params} onPageChange={handlePageChange} />
      </div>
    </PublicLayout>
  )
}

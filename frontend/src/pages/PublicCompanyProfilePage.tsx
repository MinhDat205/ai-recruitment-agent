import { Link, useParams } from 'react-router-dom'
import { PublicLayout } from '../components/layout/PublicLayout'
import { CompanyProfileCard } from '../features/companies/CompanyProfileCard'
import { useCompanyQuery } from '../features/companies/queries'

export function PublicCompanyProfilePage() {
  const { id } = useParams<{ id: string }>()
  const { data: company, isLoading, isError } = useCompanyQuery(id)

  if (isLoading) {
    return (
      <PublicLayout>
        <div className="mx-auto max-w-[1200px] px-4 py-8 md:px-6">
          <div className="h-64 animate-pulse rounded-(--radius-card) bg-canvas" />
        </div>
      </PublicLayout>
    )
  }

  if (isError || !company) {
    return (
      <PublicLayout>
        <div className="mx-auto max-w-[1200px] px-4 py-16 text-center md:px-6">
          <p className="text-sm text-ink-muted">Không tìm thấy hồ sơ doanh nghiệp.</p>
          <Link to="/" className="mt-3 inline-block text-sm text-brand hover:underline">
            Về trang danh sách việc làm
          </Link>
        </div>
      </PublicLayout>
    )
  }

  return (
    <PublicLayout>
      <div className="mx-auto max-w-[1200px] px-4 py-8 md:px-6">
        <CompanyProfileCard company={company} />
      </div>
    </PublicLayout>
  )
}

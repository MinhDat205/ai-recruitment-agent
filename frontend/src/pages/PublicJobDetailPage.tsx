import { Link, useParams } from 'react-router-dom'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { PublicLayout } from '../components/layout/PublicLayout'
import { useAuth } from '../features/auth/useAuth'
import { ApplyButton } from '../features/jobs/ApplyButton'
import { jobLocationText } from '../features/jobs/catalogDisplay'
import { useJobDetailQuery } from '../features/jobs/queries'
import { formatDeadline } from '../lib/date'

function formatSalary(job: {
  salaryMin: number | null
  salaryMax: number | null
  salaryCurrency: string | null
}): string | null {
  if (job.salaryMin == null && job.salaryMax == null) {
    return null
  }
  const currency = job.salaryCurrency ?? 'VND'
  const format = (value: number) => value.toLocaleString('vi-VN')
  if (job.salaryMin != null && job.salaryMax != null) {
    return `${format(job.salaryMin)} - ${format(job.salaryMax)} ${currency}`
  }
  const value = job.salaryMin ?? job.salaryMax
  return value != null ? `${format(value)} ${currency}` : null
}

export function PublicJobDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { data: job, isLoading, isError } = useJobDetailQuery(id)
  const { user } = useAuth()
  // R-L2: "/jobs/:id" dung chung cho moi vai tro, khong tao route rieng. Vai tro CANDIDATE boc
  // CandidateLayout (giu nav khu vuc ung vien); moi truong hop khac (khach, HR, chua dang nhap) ->
  // PublicLayout nhu hien tai. Mot bien duy nhat cho ca 3 nhanh return ben duoi - sua tai cho ca
  // 3 ma khong lap lai dieu kien.
  const Layout = user?.role === 'CANDIDATE' ? CandidateLayout : PublicLayout

  if (isLoading) {
    return (
      <Layout>
        <div className="mx-auto max-w-[1200px] px-4 py-8 md:px-6">
          <div className="h-64 animate-pulse rounded-(--radius-card) bg-canvas" />
        </div>
      </Layout>
    )
  }

  if (isError || !job) {
    return (
      <Layout>
        <div className="mx-auto max-w-[1200px] px-4 py-16 text-center md:px-6">
          <p className="text-sm text-ink-muted">Không tìm thấy tin tuyển dụng.</p>
          <Link to="/" className="mt-3 inline-block text-sm text-brand hover:underline">
            Về trang danh sách việc làm
          </Link>
        </div>
      </Layout>
    )
  }

  const salary = formatSalary(job)
  const locationText = jobLocationText(job)

  return (
    <Layout>
      <div className="mx-auto grid max-w-[1200px] grid-cols-1 gap-8 px-4 py-8 md:px-6 lg:grid-cols-[2fr_1fr]">
        <div>
          <h1 className="text-2xl font-semibold text-ink">{job.title}</h1>

          <div className="mt-3 flex flex-wrap gap-2">
            {/* FR-C05 R-J7: nhan cua ma -> gia tri cu -> "Lam tu xa"; thieu han thi an chip (UI.md 4d). */}
            {locationText && (
              <span className="rounded-(--radius-badge) bg-brand-light px-3 py-1 text-xs text-brand">
                {locationText}
              </span>
            )}
            {job.employmentType && (
              <span className="rounded-(--radius-badge) bg-brand-light px-3 py-1 text-xs text-brand">
                {job.employmentType}
              </span>
            )}
            {job.workMode && (
              <span className="rounded-(--radius-badge) bg-brand-light px-3 py-1 text-xs text-brand">
                {job.workMode}
              </span>
            )}
            <span className="rounded-(--radius-badge) bg-brand-light px-3 py-1 text-xs text-brand">
              Hạn nộp: {formatDeadline(job.deadline)}
            </span>
          </div>

          {/* R-L1b: cung token text-m3-tertiary nhu JobCard (R-L1) - text-accent-dark cu chi dat
              4.34:1, duoi nguong AA 4.5:1. */}
          {salary && <p className="mt-3 text-lg font-medium text-m3-tertiary">{salary}</p>}

          <div className="mt-6">
            <h2 className="mb-2 text-base font-semibold text-ink">Mô tả công việc</h2>
            <p className="whitespace-pre-wrap text-sm text-ink">{job.description}</p>
          </div>

          {job.requirements && (
            <div className="mt-6">
              <h2 className="mb-2 text-base font-semibold text-ink">Yêu cầu</h2>
              <p className="whitespace-pre-wrap text-sm text-ink">{job.requirements}</p>
            </div>
          )}

          <div className="mt-8">
            <ApplyButton jobId={job.id} />
          </div>
        </div>

        {job.company && (
          <Link
            to={`/companies/${job.company.id}`}
            className="flex h-fit flex-col gap-3 rounded-(--radius-card) border border-line bg-surface p-4 hover:border-brand"
          >
            <div className="h-16 w-16 overflow-hidden rounded-(--radius-badge) border border-line bg-canvas">
              {job.company.logoUrl && (
                <img src={job.company.logoUrl} alt={job.company.name} className="h-full w-full object-cover" />
              )}
            </div>
            <p className="text-sm font-medium text-ink hover:text-brand">{job.company.name}</p>
          </Link>
        )}
      </div>
    </Layout>
  )
}

import { Building2 } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { PublicLayout } from '../components/layout/PublicLayout'
import { useAuth } from '../features/auth/useAuth'
import { ApplyButton } from '../features/jobs/ApplyButton'
import { jobLocationText } from '../features/jobs/catalogDisplay'
import { formatSalary } from '../features/jobs/formatSalary'
import { EMPLOYMENT_TYPE_LABELS, WORK_MODE_LABELS } from '../features/jobs/jobLabels'
import { useJobDetailQuery } from '../features/jobs/queries'
import { formatDeadline } from '../lib/date'

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
          <div className="h-64 animate-pulse rounded-(--radius-card) bg-m3-surface-container" />
        </div>
      </Layout>
    )
  }

  if (isError || !job) {
    return (
      <Layout>
        <div className="mx-auto max-w-[1200px] px-4 py-16 text-center md:px-6">
          <p className="text-sm text-m3-on-surface">Không tìm thấy tin tuyển dụng.</p>
          <Link to="/" className="mt-3 inline-block text-sm text-m3-on-primary-container hover:underline">
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
          <h1 className="text-2xl font-semibold text-m3-on-surface">{job.title}</h1>

          <div className="mt-3 flex flex-wrap gap-2">
            {/* FR-C05 R-J7: nhan cua ma -> gia tri cu -> "Lam tu xa"; thieu han thi an chip (UI.md 4d). */}
            {locationText && (
              <span className="rounded-(--radius-badge) bg-m3-primary-container px-3 py-1 text-xs text-m3-on-primary-container">
                {locationText}
              </span>
            )}
            {job.employmentType && (
              <span className="rounded-(--radius-badge) bg-m3-primary-container px-3 py-1 text-xs text-m3-on-primary-container">
                {EMPLOYMENT_TYPE_LABELS[job.employmentType] ?? job.employmentType}
              </span>
            )}
            {job.workMode && (
              <span className="rounded-(--radius-badge) bg-m3-primary-container px-3 py-1 text-xs text-m3-on-primary-container">
                {WORK_MODE_LABELS[job.workMode] ?? job.workMode}
              </span>
            )}
            <span className="rounded-(--radius-badge) bg-m3-primary-container px-3 py-1 text-xs text-m3-on-primary-container">
              Hạn nộp: {formatDeadline(job.deadline)}
            </span>
          </div>

          {/* R-L1b: cung token text-m3-tertiary nhu JobCard (R-L1) - mau xanh la dam cu chi dat
              4.34:1, duoi nguong AA 4.5:1. */}
          {salary && <p className="mt-3 text-lg font-medium text-m3-tertiary">{salary}</p>}

          <div className="mt-6">
            <h2 className="mb-2 text-base font-semibold text-m3-on-surface">Mô tả công việc</h2>
            <p className="whitespace-pre-wrap text-sm text-m3-on-surface">{job.description}</p>
          </div>

          {job.requirements && (
            <div className="mt-6">
              <h2 className="mb-2 text-base font-semibold text-m3-on-surface">Yêu cầu</h2>
              <p className="whitespace-pre-wrap text-sm text-m3-on-surface">{job.requirements}</p>
            </div>
          )}

          <div className="mt-8">
            <ApplyButton jobId={job.id} />
          </div>
        </div>

        {job.company && (
          <Link
            to={`/companies/${job.company.id}`}
            className="flex h-fit flex-col gap-3 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-4 hover:border-m3-primary"
          >
            {/* O logo trong -> icon Building2 du phong, giong JobCard (FR-U07). */}
            <div className="flex h-16 w-16 items-center justify-center overflow-hidden rounded-(--radius-badge) border border-m3-outline-variant bg-m3-surface-container">
              {job.company.logoUrl ? (
                <img src={job.company.logoUrl} alt={job.company.name} className="h-full w-full object-cover" />
              ) : (
                <Building2 className="h-6 w-6 text-m3-on-surface-variant" aria-hidden="true" />
              )}
            </div>
            <p className="text-sm font-medium text-m3-on-surface hover:text-m3-primary">{job.company.name}</p>
          </Link>
        )}
      </div>
    </Layout>
  )
}

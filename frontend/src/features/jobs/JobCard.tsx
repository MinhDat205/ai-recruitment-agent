import { Link } from 'react-router-dom'
import { formatDeadline } from '@/lib/date'
import { jobCategoryText, jobLocationText } from './catalogDisplay'
import type { JobSummary } from './types'

function formatSalary(job: JobSummary): string | null {
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

export function JobCard({ job }: { job: JobSummary }) {
  const salary = formatSalary(job)
  const locationText = jobLocationText(job)
  const categoryText = jobCategoryText(job)

  return (
    <Link
      to={`/jobs/${job.id}`}
      className="flex gap-4 rounded-(--radius-card) border border-line bg-surface p-4 transition hover:border-brand hover:shadow-sm"
    >
      <div className="h-20 w-20 shrink-0 overflow-hidden rounded-(--radius-badge) border border-line bg-canvas">
        {job.company?.logoUrl && (
          <img src={job.company.logoUrl} alt={job.company.name} className="h-full w-full object-cover" />
        )}
      </div>

      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <h3 className="line-clamp-2 text-base font-medium text-ink hover:text-brand">{job.title}</h3>
        {job.company && <p className="text-sm text-ink-muted">{job.company.name}</p>}

        {salary && <p className="text-sm font-medium text-accent-dark">{salary}</p>}
        <p className="text-xs text-ink-muted">Hạn nộp: {formatDeadline(job.deadline)}</p>

        {/* FR-C05 R-J7: nhan cua ma -> gia tri cu -> "Lam tu xa". Thieu han thi AN chip (ngoai le da
            duyet, UI.md muc 4d); khong hien nhan "Chua chuan hoa" o phia cong khai. */}
        <div className="mt-1 flex flex-wrap gap-2">
          {locationText && (
            <span className="rounded-(--radius-badge) bg-brand-light px-3 py-1 text-xs text-brand">
              {locationText}
            </span>
          )}
          {categoryText && (
            <span className="rounded-(--radius-badge) bg-brand-light px-3 py-1 text-xs text-brand">
              {categoryText}
            </span>
          )}
        </div>
      </div>
    </Link>
  )
}

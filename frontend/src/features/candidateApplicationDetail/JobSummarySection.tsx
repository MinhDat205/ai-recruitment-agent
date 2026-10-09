import { Fragment, useId } from 'react'
import { Link } from 'react-router-dom'
import { formatDeadline } from '../../lib/date'
import { jobCategoryText, jobLocationText } from '../jobs/catalogDisplay'
import { formatSalary } from '../jobs/formatSalary'
import { EMPLOYMENT_TYPE_LABELS, WORK_MODE_LABELS } from '../jobs/jobLabels'
import { JOB_AVAILABILITY_LABELS } from './jobAvailabilityLabels'
import type { ApplicationCandidateDetail } from './types'

const MISSING_TEXT = 'Chưa có dữ liệu'

// Khoi "Tin tuyển dụng" (R-T8, R-D3..R-D6, R-G4). Tinh trang tin LAY NGUYEN tu backend (availability) -
// frontend KHONG tu suy tu deadline. Lien ket xem tin chi khi OPEN (R-D5): trang tin cong khai chi mo tin
// OPEN con han, gia tri khac se dan toi 404.
export function JobSummarySection({ job }: { job: ApplicationCandidateDetail['job'] }) {
  const headingId = useId()

  // R-G4 - o thieu hien "Chưa có dữ liệu", khong an dong; luong khong cong bo hien "Không công bố". Han nop
  // null la tin KHONG gioi han han nop (R-D4 cung coi la OPEN), khong phai thieu du lieu -> formatDeadline
  // tra "Không giới hạn", nhu trang tin cong khai va the viec lam.
  const rows: [string, string][] = [
    ['Ngành nghề', jobCategoryText(job) ?? MISSING_TEXT],
    ['Khu vực', jobLocationText(job) ?? MISSING_TEXT],
    ['Hình thức', job.employmentType ? (EMPLOYMENT_TYPE_LABELS[job.employmentType] ?? job.employmentType) : MISSING_TEXT],
    ['Làm việc', job.workMode ? (WORK_MODE_LABELS[job.workMode] ?? job.workMode) : MISSING_TEXT],
    ['Mức lương', formatSalary(job) ?? 'Không công bố'],
    ['Hạn nộp', formatDeadline(job.deadline)],
  ]

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3 py-6 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-center gap-3">
        <h2 id={headingId} className="text-m3-title-md text-m3-on-surface">
          Tin tuyển dụng
        </h2>
        {/* R-G1 - MOT kieu trung tinh cho ca 5 gia tri, khong xanh/do. */}
        <span className="rounded-(--radius-badge) border border-m3-outline bg-m3-surface px-2 py-1 text-xs font-medium text-m3-on-surface">
          {JOB_AVAILABILITY_LABELS[job.availability]}
        </span>
      </div>
      <dl className="grid grid-cols-1 gap-x-4 gap-y-1 text-sm sm:grid-cols-[auto_1fr] lg:grid-cols-[auto_1fr_auto_1fr]">
        {rows.map(([label, value]) => (
          <Fragment key={label}>
            <dt className="text-m3-on-surface-variant">{label}</dt>
            <dd className="mb-2 break-words text-m3-on-surface sm:mb-0">{value}</dd>
          </Fragment>
        ))}
      </dl>
      <p className="text-sm text-m3-on-surface-variant">
        Thông tin tin tuyển dụng là bản hiện tại, có thể đã thay đổi sau khi bạn nộp đơn.
      </p>
      {job.availability === 'OPEN' && (
        <Link to={`/jobs/${job.id}`} className="self-start text-sm font-medium text-m3-primary hover:underline">
          Xem tin tuyển dụng →
        </Link>
      )}
    </section>
  )
}

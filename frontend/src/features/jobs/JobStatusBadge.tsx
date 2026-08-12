import { JOB_STATUS_LABELS } from './jobLabels'
import type { JobStatus } from './ownerTypes'

// UI_GUIDE.md chi dinh nghia bang mau cho trang thai DON UNG TUYEN (FR-U03), khong co bang cho
// trang thai JOB (FR-H02). Ap dung dung tinh than "trung tinh ve mat phan quyet" cua UI_GUIDE:
// khong dung --color-danger/--color-accent (do/xanh goi y tot-xau) - DRAFT khong phai trang thai
// xau, CLOSED khong phai that bai. Chi dung cac token trung tinh da co: canvas, line, brand.
const STATUS_STYLES: Record<JobStatus, string> = {
  DRAFT: 'bg-canvas text-ink-muted',
  OPEN: 'bg-brand-light text-brand',
  PAUSED: 'bg-line text-ink',
  CLOSED: 'border border-line bg-surface text-ink-muted',
}

export function JobStatusBadge({ status }: { status: JobStatus }) {
  return (
    <span
      className={`inline-flex items-center rounded-(--radius-badge) px-2 py-1 text-xs font-medium ${STATUS_STYLES[status]}`}
    >
      {JOB_STATUS_LABELS[status]}
    </span>
  )
}

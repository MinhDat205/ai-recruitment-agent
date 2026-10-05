import { JOB_STATUS_LABELS } from './jobLabels'
import type { JobStatus } from './ownerTypes'

// UI_GUIDE.md chi dinh nghia bang mau cho trang thai DON UNG TUYEN (FR-U03), khong co bang cho
// trang thai JOB (FR-H02). Ap dung dung tinh than "trung tinh ve mat phan quyet" cua UI_GUIDE:
// khong dung --color-danger/--color-accent (do/xanh goi y tot-xau) - DRAFT khong phai trang thai
// xau, CLOSED khong phai that bai. Chi dung cac token m3-* trung tinh.
const STATUS_STYLES: Record<JobStatus, string> = {
  DRAFT: 'bg-m3-surface-container text-m3-on-surface',
  OPEN: 'bg-m3-primary-container text-m3-on-primary-container',
  PAUSED: 'bg-m3-surface-container-highest text-m3-on-surface',
  CLOSED: 'border border-m3-outline-variant bg-m3-surface text-m3-on-surface-variant',
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

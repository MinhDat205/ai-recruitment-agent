import { JOB_AVAILABILITY_LABELS, type JobAvailability } from './jobAvailability'

// FR-U16 R-A5 (FR-U08 R-G1) - MOT kieu trung tinh cho ca 5 gia tri, khong xanh/do. Chuyen nguyen <span> cua
// JobSummarySection (FR-U08) ra day de trang "Tin da luu" dung chung; giao dien FR-U08 khong doi.
export function JobAvailabilityBadge({ availability }: { availability: JobAvailability }) {
  return (
    <span className="rounded-(--radius-badge) border border-m3-outline bg-m3-surface px-2 py-1 text-xs font-medium text-m3-on-surface">
      {JOB_AVAILABILITY_LABELS[availability]}
    </span>
  )
}

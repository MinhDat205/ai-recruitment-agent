import { AlertCircle, CheckCircle2, Clock, Loader2 } from 'lucide-react'
import { PARSE_STATUS_LABELS, PARSE_STATUS_STYLES } from './resumeLabels'
import type { ParseStatus } from './types'

// Icon phan biet 4 trang thai vi mau badge co tinh trung tinh (xem resumeLabels.ts) - khong dua
// het vao mau de truyen dat thong tin (UI_GUIDE.md muc 7).
const PARSE_STATUS_ICONS: Record<ParseStatus, typeof Clock> = {
  PENDING: Clock,
  PROCESSING: Loader2,
  DONE: CheckCircle2,
  FAILED: AlertCircle,
}

export function ParseStatusBadge({ status }: { status: ParseStatus }) {
  const Icon = PARSE_STATUS_ICONS[status]
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-(--radius-badge) px-2 py-1 text-xs font-medium ${PARSE_STATUS_STYLES[status]}`}
    >
      <Icon className={`h-3 w-3 ${status === 'PROCESSING' ? 'animate-spin' : ''}`} aria-hidden="true" />
      {PARSE_STATUS_LABELS[status]}
    </span>
  )
}

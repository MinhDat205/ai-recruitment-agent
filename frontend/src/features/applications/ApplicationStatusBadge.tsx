import { APPLICATION_STATUS_LABELS, APPLICATION_STATUS_STYLES } from './applicationLabels'
import type { ApplicationStatus } from './types'

export function ApplicationStatusBadge({ status }: { status: ApplicationStatus }) {
  return (
    <span
      className={`inline-flex items-center rounded-(--radius-badge) px-2 py-1 text-xs font-medium ${APPLICATION_STATUS_STYLES[status]}`}
    >
      {APPLICATION_STATUS_LABELS[status]}
    </span>
  )
}

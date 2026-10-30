import { CANDIDATE_RECONSIDERATION_TEXT as TEXT } from './reconsiderationLabels'
import type { ReconsiderationStatus } from './types'

// FR-C09 UI.md 4a - CHU " · Đang chờ xem xét lại" ngay sau ApplicationStatusBadge, chi khi yeu cau OPEN. Khong badge, khong
// mau, khong icon (UI.md 5d, muc 10): don van la "Bị từ chối", day chi la thong tin them.
export function PendingReconsiderationText({ status }: { status: ReconsiderationStatus | null | undefined }) {
  if (status !== 'OPEN') {
    return null
  }
  return <span className="text-sm text-m3-on-surface-variant">· {TEXT.pendingBesideBadge}</span>
}

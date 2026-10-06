import { useEffect, useRef } from 'react'
import { ArrowLeft } from 'lucide-react'
import { Link } from 'react-router-dom'
import { formatDateTimeVi } from '../../lib/date'
import { ApplicationStatusBadge } from '../applications/ApplicationStatusBadge'
import { ApplicationActionBar } from './ApplicationActionBar'
import type { ApplicationHrDetail } from './types'

// Dau trang ho so don (R-D1, R-E3): lien ket quay lai tab Ung vien cua tin, ten ung vien (h1 duy nhat
// cua trang), badge trang thai, ngay nop, thanh thao tac. KHONG co diem hay hang o day (R-G2).
//
// Responsive (UI.md muc 8): >= lg ten ben trai, thanh thao tac ben phai cung hang; sm-lg thanh thao
// tac xuong hang duoi, nut nam ngang; < sm moi thu xep doc, nut w-full.
export function ApplicationDetailHeader({ detail }: { detail: ApplicationHrDetail }) {
  // UI.md muc 9 - doi trang thai thanh cong thi dat focus ve badge: nut da mo hop thoai co the bien mat
  // khi trang thai doi, Radix khong tra focus ve dau duoc. Bo qua lan render dau.
  const badgeRef = useRef<HTMLSpanElement>(null)
  const previousStatusRef = useRef(detail.status)
  useEffect(() => {
    if (previousStatusRef.current !== detail.status) {
      previousStatusRef.current = detail.status
      badgeRef.current?.focus()
    }
  }, [detail.status])

  return (
    <div className="flex flex-col gap-4">
      <Link
        to={`/hr/jobs/${detail.jobId}/edit?tab=applications`}
        aria-label={`Quay lại danh sách ứng viên của tin ${detail.jobTitle}`}
        className="inline-flex items-center gap-1 self-start text-sm text-m3-primary break-words hover:underline"
      >
        <ArrowLeft className="h-4 w-4 shrink-0" aria-hidden="true" />
        {detail.jobTitle}
      </Link>
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex flex-col gap-2">
          <h1 className="text-m3-title-lg break-words text-m3-on-surface">{detail.candidateName}</h1>
          <div className="flex flex-col items-start gap-2 sm:flex-row sm:items-center sm:gap-3">
            <span ref={badgeRef} tabIndex={-1} className="rounded-(--radius-badge) outline-none focus-visible:ring-2 focus-visible:ring-m3-primary">
              <ApplicationStatusBadge status={detail.status} />
            </span>
            <span className="text-sm text-m3-on-surface-variant">Nộp ngày {formatDateTimeVi(detail.appliedAt)}</span>
          </div>
        </div>
        <ApplicationActionBar detail={detail} />
      </div>
    </div>
  )
}

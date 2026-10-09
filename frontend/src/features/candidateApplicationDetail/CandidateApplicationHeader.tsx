import { useEffect, useRef, useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { formatDateTimeVi } from '../../lib/date'
import { ApplicationStatusBadge } from '../applications/ApplicationStatusBadge'
import type { ApplicationStatus } from '../applications/types'
import { WithdrawApplicationDialog } from '../applications/WithdrawApplicationDialog'
import type { ApplicationCandidateDetail } from './types'

// R-W1 - dung dieu kien FR-U06 (ApplicationService.withdraw): chi PENDING/INTERVIEW_INVITED rut duoc.
const WITHDRAWABLE_STATUSES: ApplicationStatus[] = ['PENDING', 'INTERVIEW_INVITED']

// Dau trang chi tiet don (R-E2, R-T15, R-T16): lien ket quay lai danh sach, ten tin (h1 duy nhat), cong ty,
// badge trang thai, ngay nop, nut "Rut don". Trang thai cuoi: khong nut, khong dong chu thay the.
//
// Responsive (UI.md muc 8): >= lg nut ben phai cung hang; sm-lg nut xuong hang duoi; < sm xep doc, nut
// w-full, TRONG luong trang (khong fixed/sticky).
export function CandidateApplicationHeader({ detail }: { detail: ApplicationCandidateDetail }) {
  const [withdrawOpen, setWithdrawOpen] = useState(false)

  // UI.md muc 9 - rut don thanh cong thi dat focus ve badge: nut da mo hop thoai bien mat khi trang thai
  // doi, Radix khong tra focus ve dau duoc. Bo qua lan render dau (cung cach ApplicationDetailHeader).
  const badgeRef = useRef<HTMLSpanElement>(null)
  const previousStatusRef = useRef(detail.status)
  useEffect(() => {
    if (previousStatusRef.current !== detail.status) {
      previousStatusRef.current = detail.status
      badgeRef.current?.focus()
    }
  }, [detail.status])

  const canWithdraw = WITHDRAWABLE_STATUSES.includes(detail.status)

  return (
    <div className="flex flex-col gap-4">
      <Link
        to="/candidate/applications"
        aria-label="Quay lại danh sách đơn ứng tuyển của tôi"
        className="inline-flex items-center gap-1 self-start text-sm text-m3-primary hover:underline"
      >
        <ArrowLeft className="h-4 w-4 shrink-0" aria-hidden="true" />
        Đơn ứng tuyển của tôi
      </Link>
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex min-w-0 flex-col gap-2">
          <h1 className="text-m3-title-lg break-words text-m3-on-surface">{detail.job.title}</h1>
          <p className="text-sm break-words text-m3-on-surface-variant">{detail.job.companyName}</p>
          <div className="flex flex-col items-start gap-2 sm:flex-row sm:items-center sm:gap-3">
            <span
              ref={badgeRef}
              tabIndex={-1}
              className="rounded-(--radius-badge) outline-none focus-visible:ring-2 focus-visible:ring-m3-primary"
            >
              <ApplicationStatusBadge status={detail.status} />
            </span>
            <span className="text-sm text-m3-on-surface-variant">Nộp ngày {formatDateTimeVi(detail.appliedAt)}</span>
          </div>
        </div>
        {canWithdraw && (
          <Button
            type="button"
            variant="outline"
            className="w-full sm:w-auto sm:self-start"
            onClick={() => setWithdrawOpen(true)}
          >
            Rút đơn
          </Button>
        )}
      </div>
      <WithdrawApplicationDialog
        application={withdrawOpen ? { id: detail.id, jobTitle: detail.job.title } : null}
        onClose={() => setWithdrawOpen(false)}
      />
    </div>
  )
}

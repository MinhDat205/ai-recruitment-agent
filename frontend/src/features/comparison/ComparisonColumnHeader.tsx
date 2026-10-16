import { Info } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { formatDateTimeVi } from '../../lib/date'
import { EMPTY_VALUE_PLACEHOLDER } from '../../lib/score'
import { ApplicationStatusBadge } from '../applications/ApplicationStatusBadge'
import { InterviewInvitationDialog } from '../interviewinvitation/InterviewInvitationDialog'
import { COMPARE_COLUMN_TEXT } from './comparisonLabels'
import { comparisonInvalidateKeys } from './queries'
import type { ComparisonColumn } from './types'

// Dau cot (bang 4a) / the ung vien (bo cuc theo hang 4c) - R-D1, R-D10, R-H1, R-H2. Ten ung vien la chu thuong
// text-m3-title-md o MOI cot (khong doi kieu theo diem, R-W1); tabIndex -1 de nhan focus sau khi gui loi moi.
// "Moi phong van" CHI khi PENDING (cung dieu kien nextActionsFor cua ApplicationActionBar) va mo DUNG hop thoai co san
// (R-H2). Gui thanh cong -> lam moi C1 + danh sach theo Job + cac khoa trang ho so don (R-H3: comparisonInvalidateKeys
// da gom applicationDetailInvalidateKeys).
export function ComparisonColumnHeader({
  column,
  ordinal,
  jobId,
  variant,
}: {
  column: ComparisonColumn
  // So thu tu 1-based theo thu tu cot - chi hien o the 4c ("{i} · {ten}").
  ordinal: number
  jobId: string
  variant: 'table' | 'card'
}) {
  const [inviteOpen, setInviteOpen] = useState(false)
  const nameRef = useRef<HTMLSpanElement>(null)
  // Hop thoai khong bao rieng "gui xong" o mode invite (chi goi onOpenChange(false)), va khong duoc sua hop thoai
  // (R-H2). Nen: dong hop thoai -> cho; neu C1 tai lai voi trang thai da roi PENDING (gui thanh cong, nut moi bien
  // mat) thi dua focus ve ten ung vien. Huy hop thoai thi trang thai khong doi, Radix tra focus ve nut nhu thuong.
  const awaitingInviteResult = useRef(false)
  const canInvite = column.status === 'PENDING'

  useEffect(() => {
    if (awaitingInviteResult.current && column.status !== 'PENDING') {
      awaitingInviteResult.current = false
      nameRef.current?.focus()
    }
  }, [column.status])

  const scoredAtText = column.scoredAt ? formatDateTimeVi(column.scoredAt) : EMPTY_VALUE_PLACEHOLDER
  const isCard = variant === 'card'

  return (
    <div className="flex min-w-0 flex-col items-start gap-1.5 break-words">
      <span ref={nameRef} tabIndex={-1} className="text-m3-title-md text-m3-on-surface outline-none focus-visible:underline">
        {isCard ? COMPARE_COLUMN_TEXT.ordinal(ordinal, column.candidateName) : column.candidateName}
      </span>
      <ApplicationStatusBadge status={column.status} />
      <p className="text-m3-body-sm text-m3-on-surface-variant">
        {COMPARE_COLUMN_TEXT.appliedAt(formatDateTimeVi(column.appliedAt))}
      </p>
      {column.scoredAt && (
        <p className="text-m3-body-sm text-m3-on-surface-variant">{COMPARE_COLUMN_TEXT.scoredAt(scoredAtText)}</p>
      )}
      {column.newerRunInProgress && (
        <p className="flex items-start gap-1.5 text-m3-body-sm text-m3-on-surface">
          <Info className="mt-0.5 h-3.5 w-3.5 shrink-0 text-m3-on-surface" aria-hidden="true" />
          <span>{COMPARE_COLUMN_TEXT.newerRunInProgress(scoredAtText)}</span>
        </p>
      )}

      {/* Bang: >= lg hai nut cung hang, md-lg xep doc w-full (UI.md muc 8). The 4c: hai nut cung hang, moi nut flex-1,
          cao >= 48px; mot nut thi chiem ca hang. Cung variant outline - "Moi phong van" khong noi hon o cot nao. */}
      <div className={`mt-1 flex w-full gap-2 ${isCard ? 'flex-row' : 'flex-col lg:flex-row lg:flex-wrap'}`}>
        <Button
          asChild
          variant="outline"
          className={isCard ? 'min-h-12 flex-1' : 'min-h-12 w-full lg:min-h-0 lg:w-auto'}
        >
          <Link to={`/hr/applications/${column.applicationId}`} aria-label={COMPARE_COLUMN_TEXT.openProfileAria(column.candidateName)}>
            {COMPARE_COLUMN_TEXT.openProfile}
          </Link>
        </Button>
        {canInvite && (
          <Button
            type="button"
            variant="outline"
            className={isCard ? 'min-h-12 flex-1' : 'min-h-12 w-full lg:min-h-0 lg:w-auto'}
            aria-label={COMPARE_COLUMN_TEXT.inviteAria(column.candidateName)}
            onClick={() => {
              awaitingInviteResult.current = false
              setInviteOpen(true)
            }}
          >
            {COMPARE_COLUMN_TEXT.invite}
          </Button>
        )}
      </div>

      <InterviewInvitationDialog
        application={inviteOpen ? { id: column.applicationId, candidateName: column.candidateName } : null}
        jobId={jobId}
        onOpenChange={(open) => {
          if (!open) {
            setInviteOpen(false)
            awaitingInviteResult.current = true
          }
        }}
        invalidateQueryKeys={comparisonInvalidateKeys(jobId, column.applicationId)}
      />
    </div>
  )
}

import { useEffect, useRef, useState } from 'react'
import { Archive, ArrowLeft } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { formatDateTimeVi } from '../../lib/date'
import { ApplicationStatusBadge } from '../applications/ApplicationStatusBadge'
import { BLIND_REVIEW_TEXT } from '../blindReview/blindReviewLabels'
import { ReconsiderationCard } from '../reconsideration/ReconsiderationCard'
import { useReconsiderationDialogs } from '../reconsideration/useReconsiderationDialogs'
import { HR_RECONSIDERATION_TEXT } from '../reconsideration/reconsiderationLabels'
import { SaveToTalentPoolDialog } from '../talentPools/SaveToTalentPoolDialog'
import { TALENT_POOL_TEXT } from '../talentPools/talentPoolLabels'
import { ApplicationActionBar } from './ApplicationActionBar'
import type { ApplicationHrDetail } from './types'

// Dau trang ho so don (R-D1, R-E3): lien ket quay lai tab Ung vien cua tin, ten ung vien (h1 duy nhat
// cua trang), badge trang thai, ngay nop, thanh thao tac. KHONG co diem hay hang o day (R-G2).
//
// Responsive (UI.md muc 8): >= lg ten ben trai, thanh thao tac ben phai cung hang; sm-lg thanh thao
// tac xuong hang duoi, nut nam ngang; < sm moi thu xep doc, nut w-full.
// FR-H15 UI.md 4d - nut "Luu vao kho" (Outlined) o cum ben phai, SAU thanh thao tac H07, cach bang gap-2, KHONG nam
// trong ApplicationActionBar (khong doi thu tu/kieu nut H07). Nut luon hien; hop thoai tu giai thich khi don khong co
// dong y. Luu xong: cau "Da cap nhat kho ung vien." canh nut (aria-live), focus ve nut.
// FR-H16 UI.md 4c - don dang an danh: badge trung tinh "An danh" canh badge trang thai; "Luu vao kho" khoa (disabled +
// aria-describedby cau ly do), khong mo hop thoai. Backend van chan (R-K2) - nut khoa chi de ro ly do.
const SAVE_TO_POOL_REASON_ID = 'save-to-pool-blocked-reason'

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

  const [saveOpen, setSaveOpen] = useState(false)
  const [saved, setSaved] = useState(false)
  const saveButtonRef = useRef<HTMLButtonElement>(null)

  // FR-C09 UI.md 4f - khoi yeu cau xem xet lai: giu tu choi xong, the van con (doi sang DECLINED) -> focus ve tieu de khoi.
  const reconsiderationHeadingRef = useRef<HTMLHeadingElement>(null)
  const [reconsiderationDeclined, setReconsiderationDeclined] = useState(false)
  const reconsiderationDialogs = useReconsiderationDialogs(detail.jobId, () => {
    setReconsiderationDeclined(true)
    reconsiderationHeadingRef.current?.focus()
  })

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
            {detail.identityMasked && (
              <span className="inline-flex items-center rounded-(--radius-badge) border border-m3-outline-variant bg-m3-surface-container px-2 py-1 text-xs font-medium text-m3-on-surface-variant">
                {BLIND_REVIEW_TEXT.badge}
              </span>
            )}
            <span className="text-sm text-m3-on-surface-variant">Nộp ngày {formatDateTimeVi(detail.appliedAt)}</span>
          </div>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-start">
          <ApplicationActionBar detail={detail} />
          <div className="flex flex-col gap-1">
            <Button
              ref={saveButtonRef}
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              disabled={detail.identityMasked}
              aria-describedby={detail.identityMasked ? SAVE_TO_POOL_REASON_ID : undefined}
              onClick={() => {
                setSaved(false)
                setSaveOpen(true)
              }}
            >
              <Archive aria-hidden="true" />
              {TALENT_POOL_TEXT.saveToPool}
            </Button>
            {detail.identityMasked && (
              <p id={SAVE_TO_POOL_REASON_ID} className="max-w-xs text-sm text-m3-on-surface-variant">
                {BLIND_REVIEW_TEXT.saveToPoolBlocked}
              </p>
            )}
            {/* Vung aria-live luon co trong DOM de trinh doc man hinh doc duoc khi noi dung xuat hien. */}
            <p aria-live="polite" className="text-sm text-m3-on-surface-variant">
              {saved ? TALENT_POOL_TEXT.saved : ''}
            </p>
          </div>
        </div>
      </div>
      {/* FR-C09 UI.md 4f - ngay duoi thanh thao tac, tren TabsList; chi khi don co yeu cau (moi trang thai). */}
      {detail.reconsideration && (
        <ReconsiderationCard
          item={detail.reconsideration}
          variant="detail"
          onInvite={reconsiderationDialogs.openInvite}
          onDecline={reconsiderationDialogs.openDecline}
          headingRef={reconsiderationHeadingRef}
        />
      )}
      <p aria-live="polite" className="sr-only">
        {reconsiderationDeclined ? HR_RECONSIDERATION_TEXT.declinedAnnouncement : ''}
      </p>
      {reconsiderationDialogs.dialogs}
      <SaveToTalentPoolDialog
        applicationId={detail.id}
        candidateName={detail.candidateName}
        open={saveOpen}
        onOpenChange={(open) => {
          setSaveOpen(open)
          // Radix tra focus ve phan tu da mo hop thoai; dat lai tuong minh cho chac (UI.md 4d, muc 9).
          if (!open) requestAnimationFrame(() => saveButtonRef.current?.focus())
        }}
        onSaved={() => setSaved(true)}
      />
    </div>
  )
}

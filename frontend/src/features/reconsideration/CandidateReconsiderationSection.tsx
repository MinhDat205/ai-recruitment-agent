import { useId, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { formatDateTimeVi } from '../../lib/date'
import { CANDIDATE_RECONSIDERATION_TEXT as TEXT } from './reconsiderationLabels'
import { ReconsiderationRequestDialog } from './ReconsiderationRequestDialog'
import type { CandidateReconsideration } from './types'

interface CandidateReconsiderationSectionProps {
  applicationId: string
  reconsideration: CandidateReconsideration | null
}

// Doan van ban nguoi dung go (ly do / phan hoi): hien DAY DU, nguyen van, khong render HTML (R-V4, UI.md 4b).
function QuotedText({ label, text }: { label: string; text: string }) {
  return (
    <div className="flex flex-col gap-1">
      <p className="text-sm text-m3-on-surface-variant">{label}</p>
      <p className="text-m3-body-md whitespace-pre-wrap break-words text-m3-on-surface">{text}</p>
    </div>
  )
}

// Khoi "Xem xét lại" (FR-C09 UI.md 4b) - dau tab "Thông tin đơn". Doc khoa reconsideration cua E1 (khong goi API rieng);
// null -> khong render. state va deadline do backend tinh (R-T4): KHONG so han voi dong ho trinh duyet, chi hien thi.
// Mau trung tinh, khong badge/mau theo ket qua (UI.md muc 10). Khong co diem, rubric, ten HR, decidedBy (R-C3).
export function CandidateReconsiderationSection({ applicationId, reconsideration }: CandidateReconsiderationSectionProps) {
  const headingId = useId()
  const headingRef = useRef<HTMLHeadingElement>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [announced, setAnnounced] = useState(false)

  if (!reconsideration) {
    return null
  }

  const { state, deadline, requestedAt, reason, decidedAt, hrResponse } = reconsideration
  // Ly do chi co khi da gui; reason null (ung vien khong nhap) -> bo ca nhan "Lý do bạn đã gửi" (UI.md 4b).
  const sentReason = state === 'OPEN' || state === 'ACCEPTED' || state === 'DECLINED' ? reason : null

  let statusText: string
  switch (state) {
    case 'AVAILABLE':
      statusText = TEXT.available
      break
    case 'EXPIRED':
      statusText = TEXT.expired(formatDateTimeVi(deadline))
      break
    case 'JOB_REMOVED':
      statusText = TEXT.jobRemoved
      break
    case 'OPEN':
      statusText = requestedAt ? TEXT.open(formatDateTimeVi(requestedAt)) : TEXT.open('')
      break
    case 'ACCEPTED':
      statusText = decidedAt ? TEXT.accepted(formatDateTimeVi(decidedAt)) : TEXT.accepted('')
      break
    case 'DECLINED':
      statusText = decidedAt ? TEXT.declined(formatDateTimeVi(decidedAt)) : TEXT.declined('')
      break
  }

  return (
    <section aria-labelledby={headingId} className="flex min-w-0 flex-col py-6 first:pt-0 last:pb-0">
      <div className="flex min-w-0 flex-col gap-3 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-4">
        <h2
          id={headingId}
          ref={headingRef}
          tabIndex={-1}
          className="text-m3-title-md font-medium text-m3-on-surface outline-none focus-visible:ring-2 focus-visible:ring-m3-primary"
        >
          {TEXT.sectionTitle}
        </h2>
        <p className="text-m3-body-md break-words text-m3-on-surface">{statusText}</p>
        {state === 'AVAILABLE' && (
          <>
            <p className="text-sm text-m3-on-surface-variant">{TEXT.deadline(formatDateTimeVi(deadline))}</p>
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto sm:self-start"
              onClick={() => setDialogOpen(true)}
            >
              {TEXT.requestButton}
            </Button>
          </>
        )}
        {state === 'DECLINED' &&
          (hrResponse !== null ? (
            <QuotedText label={TEXT.hrResponseLabel} text={hrResponse} />
          ) : (
            <p className="text-sm text-m3-on-surface-variant">{TEXT.noHrResponse}</p>
          ))}
        {sentReason !== null && <QuotedText label={TEXT.reasonLabel} text={sentReason} />}
      </div>
      <p aria-live="polite" className="sr-only">
        {announced ? TEXT.submittedAnnouncement : ''}
      </p>
      {/* Chi MO duoc khi AVAILABLE (nut chi co o AVAILABLE); khong ep dong khi E1 doi sau 409 de cau loi van hien. */}
      <ReconsiderationRequestDialog
        application={dialogOpen ? { id: applicationId, deadline } : null}
        onClose={() => setDialogOpen(false)}
        onSubmitted={() => {
          setAnnounced(true)
          headingRef.current?.focus()
        }}
      />
    </section>
  )
}

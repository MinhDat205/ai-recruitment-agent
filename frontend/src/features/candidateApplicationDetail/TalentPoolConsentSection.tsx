import { useId, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { formatDateTimeVi } from '../../lib/date'
import { TALENT_POOL_TEXT } from '../candidateScreening/candidateScreeningLabels'
import { RevokeTalentPoolConsentDialog } from './RevokeTalentPoolConsentDialog'
import type { ApplicationCandidateDetail } from './types'

// Khoi "Lưu hồ sơ cho đợt tuyển sau" (FR-U09 UI.md 4e, 4f) - LUON render, doc khoa talentPoolConsent cua E1 (khong goi
// API rieng). Ba trang thai do backend tinh (R-K5). Nut "Rut dong y" CHI co o ACTIVE (R-K4: khong co nut dong y lai).
// Mau trung tinh, khong badge, khong to mau theo trang thai (R-N3); nut outline, khong do (UI.md 5d).
export function TalentPoolConsentSection({ detail }: { detail: ApplicationCandidateDetail }) {
  const headingId = useId()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [announced, setAnnounced] = useState(false)
  // Cau trang thai giu NGUYEN mot phan tu qua ca ba trang thai, de sau khi rut xong (nut mo hop thoai bien mat) dat
  // focus vao day (UI.md muc 9, cung cach tra focus ve badge cua FR-U08).
  const statusRef = useRef<HTMLParagraphElement>(null)
  const consent = detail.talentPoolConsent

  let statusText: string
  if (consent.state === 'ACTIVE' && consent.consentedAt) {
    statusText = TALENT_POOL_TEXT.active(detail.job.companyName, formatDateTimeVi(consent.consentedAt))
  } else if (consent.state === 'REVOKED' && consent.revokedAt) {
    statusText = TALENT_POOL_TEXT.revoked(formatDateTimeVi(consent.revokedAt))
  } else {
    statusText = TALENT_POOL_TEXT.notGiven
  }
  const isActive = consent.state === 'ACTIVE'

  return (
    <section aria-labelledby={headingId} className="flex min-w-0 flex-col gap-3 py-6 first:pt-0 last:pb-0">
      <h2 id={headingId} className="text-m3-title-md text-m3-on-surface">
        {TALENT_POOL_TEXT.title}
      </h2>
      <p
        ref={statusRef}
        tabIndex={-1}
        className="text-m3-body-md break-words text-m3-on-surface outline-none focus-visible:ring-2 focus-visible:ring-m3-primary"
      >
        {statusText}
      </p>
      {isActive && (
        <>
          <p className="text-sm break-words text-m3-on-surface-variant">{TALENT_POOL_TEXT.activeNote}</p>
          <Button
            type="button"
            variant="outline"
            className="w-full sm:w-auto sm:self-start"
            onClick={() => setDialogOpen(true)}
          >
            {TALENT_POOL_TEXT.revokeButton}
          </Button>
        </>
      )}
      <p aria-live="polite" className="sr-only">
        {announced ? TALENT_POOL_TEXT.revokedAnnouncement : ''}
      </p>
      {/* Chi MO duoc khi ACTIVE (nut chi co o ACTIVE); khong ep dong khi E1 doi sau 409 de cau loi van hien trong hop. */}
      <RevokeTalentPoolConsentDialog
        application={dialogOpen ? { id: detail.id, companyName: detail.job.companyName } : null}
        onClose={() => setDialogOpen(false)}
        onRevoked={() => {
          setAnnounced(true)
          statusRef.current?.focus()
        }}
      />
    </section>
  )
}

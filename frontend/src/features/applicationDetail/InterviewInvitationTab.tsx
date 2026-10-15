import { AlertCircle, ChevronDown, ChevronUp } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { formatDateTimeVi } from '../../lib/date'
import { InterviewInvitationDialog } from '../interviewinvitation/InterviewInvitationDialog'
import { InterviewSlotList } from '../interviewinvitation/InterviewSlotList'
import { INVITATION_TEXT } from '../interviewinvitation/invitationLabels'
import {
  CandidatePickedLine,
  InvitationLetter,
  PreviousInvitationCard,
} from '../interviewinvitation/PreviousInvitationCard'
import { useInterviewInvitationsQuery } from '../interviewinvitation/queries'
import { applicationDetailInvalidateKeys } from './queries'
import type { ApplicationHrDetail } from './types'

// Tab "Giay moi phong van" (FR-H12 UI.md 4c, 4f, muc 6) - goi I1 khi tab mo (Radix chi render tab dang mo). Hien o MOI
// trang thai don (khong an tab). Giay moi hieu luc: khung (gio Viet Nam), khung chot, thu moi NGUYEN VAN, nut "Gui lai giay
// moi" CHI khi don INTERVIEW_INVITED. Giay moi cu: thu gon mac dinh, thu tu I1 (moi bi thay gan nhat truoc). KHONG lap nut
// "Moi phong van" (nut o thanh thao tac, Q7). Khong nut sua/xoa (R-C2). Dia diem la chu thuong, khong <a> (B4).
export function InterviewInvitationTab({ detail }: { detail: ApplicationHrDetail }) {
  const { data, isLoading, isError, refetch } = useInterviewInvitationsQuery(detail.id)
  const [resendOpen, setResendOpen] = useState(false)
  const [previousOpen, setPreviousOpen] = useState(false)
  const headingRef = useRef<HTMLHeadingElement>(null)
  // UI.md muc 6, 9 - gui lai thanh cong: focus ve tieu de "Giay moi hien tai" SAU khi I1 tai lai giay moi moi.
  const focusHeadingOnNext = useRef(false)

  const active = data?.invitations.find((invitation) => invitation.active) ?? null
  const previous = data?.invitations.filter((invitation) => !invitation.active) ?? []

  useEffect(() => {
    if (focusHeadingOnNext.current && active) {
      focusHeadingOnNext.current = false
      headingRef.current?.focus()
    }
  }, [active])

  if (isLoading) {
    return <InvitationSkeleton />
  }
  if (isError || !data) {
    return (
      <div role="alert" className="flex flex-col items-start gap-2">
        <p className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          {INVITATION_TEXT.loadFailed}
        </p>
        <Button type="button" variant="outline" size="sm" onClick={() => void refetch()}>
          {INVITATION_TEXT.retry}
        </Button>
      </div>
    )
  }

  if (data.invitations.length === 0) {
    return (
      <p className="text-m3-body-md break-words text-m3-on-surface">
        {detail.status === 'PENDING' ? INVITATION_TEXT.emptyPending : INVITATION_TEXT.emptyOther}
      </p>
    )
  }

  const canResend = detail.status === 'INTERVIEW_INVITED' && active !== null
  const previousListId = `previous-invitations-${detail.id}`

  return (
    <section className="flex min-w-0 flex-col gap-4">
      {active && (
        <div className="flex min-w-0 flex-col gap-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div className="flex min-w-0 flex-col gap-1">
              <h2
                ref={headingRef}
                tabIndex={-1}
                className="text-m3-title-md text-m3-on-surface outline-none focus-visible:ring-2 focus-visible:ring-m3-primary"
              >
                {INVITATION_TEXT.activeTitle}
              </h2>
              <p className="text-sm text-m3-on-surface-variant">
                {INVITATION_TEXT.sentAt(formatDateTimeVi(active.sentAt))}
              </p>
              {/* FR-U10 UI.md 4d - ngay duoi "Gui luc", ke ca 375px (muc 8) */}
              <CandidatePickedLine invitation={active} />
            </div>
            {canResend && (
              <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => setResendOpen(true)}>
                {INVITATION_TEXT.resendButton}
              </Button>
            )}
          </div>
          {active.confirmedSlotId === null && (
            <p className="text-sm break-words text-m3-on-surface">{INVITATION_TEXT.unconfirmed}</p>
          )}
          <InterviewSlotList slots={active.slots} confirmedSlotId={active.confirmedSlotId} variant="hr" />
          <InvitationLetter subject={active.subject} content={active.renderedContent} />
        </div>
      )}

      {previous.length > 0 && (
        <div className="flex min-w-0 flex-col gap-3">
          <Button
            type="button"
            variant="ghost"
            className="self-start px-2 text-m3-on-surface"
            aria-expanded={previousOpen}
            aria-controls={previousListId}
            onClick={() => setPreviousOpen((value) => !value)}
          >
            {previousOpen ? (
              <ChevronUp className="h-4 w-4" aria-hidden="true" />
            ) : (
              <ChevronDown className="h-4 w-4" aria-hidden="true" />
            )}
            {INVITATION_TEXT.previousToggle(previous.length)}
          </Button>
          <div id={previousListId} hidden={!previousOpen} className="flex min-w-0 flex-col gap-3">
            {previous.map((invitation) => (
              <PreviousInvitationCard key={invitation.id} invitation={invitation} />
            ))}
          </div>
        </div>
      )}

      {canResend && (
        <InterviewInvitationDialog
          mode="resend"
          application={resendOpen ? { id: detail.id, candidateName: detail.candidateName } : null}
          jobId={detail.jobId}
          activeInvitation={active}
          maxSlots={data.maxSlots}
          invalidateQueryKeys={applicationDetailInvalidateKeys(detail.id, detail.jobId)}
          onOpenChange={(open) => !open && setResendOpen(false)}
          onResent={() => {
            focusHeadingOnNext.current = true
          }}
        />
      )}
    </section>
  )
}

function InvitationSkeleton() {
  return (
    <div className="flex flex-col gap-4" aria-hidden="true">
      {[0, 1, 2].map((i) => (
        <div key={i} className="flex flex-col gap-2">
          <div className="h-5 w-2/3 animate-pulse rounded-m3-xs bg-m3-surface-container" />
          <div className="h-4 w-1/3 animate-pulse rounded-m3-xs bg-m3-surface-container" />
          <div className="h-4 w-1/2 animate-pulse rounded-m3-xs bg-m3-surface-container" />
        </div>
      ))}
    </div>
  )
}

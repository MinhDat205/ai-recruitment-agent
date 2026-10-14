import { isAxiosError } from 'axios'
import { useId, useRef } from 'react'
import { Button } from '@/components/ui/button'
import type { ApplicationStatus } from '../applications/types'
import { InterviewInvitationDetails } from '../interviewinvitation/InterviewInvitationDetails'
import { useInterviewInvitationQuery } from '../interviewinvitation/queries'

// R-T3/R-T4 - dung quy tac hien giay moi cu cua trang danh sach (CandidateApplicationsPage, truoc FR-U08):
// don da tung duoc moi co the da sang HIRED/REJECTED, ung vien van can xem lai lich hen.
const INVITATION_VIEWABLE_STATUSES: ApplicationStatus[] = ['INTERVIEW_INVITED', 'HIRED', 'REJECTED']

// Khoi "Giấy mời phỏng vấn" (R-T3..R-T7). Goi cung hook/khoa query voi InterviewInvitationDetails (TanStack
// gop thanh mot request) de biet 404 ma an CA khoi ke ca tieu de (R-T5) - khong truyen notFoundFallback.
// FR-U10 (UI.md 4c, 5b): truyen status xuong de chon hien thi phan chon khung; h2 tabIndex={-1} + ref de nhan focus sau
// khi xac nhan khung thanh cong (L28), vung aria-live co san doc cau "Bạn đã chọn ...".
export function InvitationSection({ applicationId, status }: { applicationId: string; status: ApplicationStatus }) {
  const headingId = useId()
  const headingRef = useRef<HTMLHeadingElement>(null)
  const viewable = INVITATION_VIEWABLE_STATUSES.includes(status)
  const invitationQuery = useInterviewInvitationQuery(applicationId, viewable)

  if (!viewable) {
    return null
  }

  const notFound =
    invitationQuery.isError &&
    isAxiosError(invitationQuery.error) &&
    invitationQuery.error.response?.status === 404
  if (notFound) {
    return null
  }

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3 py-6 first:pt-0 last:pb-0">
      <h2
        id={headingId}
        ref={headingRef}
        tabIndex={-1}
        className="text-m3-title-md text-m3-on-surface outline-none focus-visible:ring-2 focus-visible:ring-m3-primary"
      >
        Giấy mời phỏng vấn
      </h2>
      <div aria-live="polite" className="flex flex-col items-start gap-2 break-words">
        <InterviewInvitationDetails applicationId={applicationId} status={status} headingRef={headingRef} />
        {/* R-T7 - loi khac 404 (mang/5xx): cau loi co san cua InterviewInvitationDetails + nut "Thử lại". */}
        {invitationQuery.isError && (
          <Button type="button" variant="outline" size="sm" onClick={() => invitationQuery.refetch()}>
            Thử lại
          </Button>
        )}
      </div>
    </section>
  )
}

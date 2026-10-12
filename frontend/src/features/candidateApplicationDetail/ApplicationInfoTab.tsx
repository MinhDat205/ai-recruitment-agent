import { InvitationSection } from './InvitationSection'
import { JobSummarySection } from './JobSummarySection'
import { ParsedResumeSection } from './ParsedResumeSection'
import { ScreeningAnswersSection } from './ScreeningAnswersSection'
import { SubmittedResumeSection } from './SubmittedResumeSection'
import { TalentPoolConsentSection } from './TalentPoolConsentSection'
import type { ApplicationCandidateDetail } from './types'

// Tab "Thông tin đơn" - cac khoi theo thu tu CO DINH (UI.md muc 4a cua FR-U08): Giay moi (chi khi co) -> Tin tuyen
// dung -> Ho so da nop -> [FR-U09: Cau tra loi sang loc (chi khi CAPTURED) -> Luu ho so cho dot tuyen sau] -> CV da
// trich xuat. divide-y ke vach giua cac khoi dang hien; khoi an han (null) thi khong de lai vach thua.
export function ApplicationInfoTab({ detail }: { detail: ApplicationCandidateDetail }) {
  return (
    <div className="flex flex-col divide-y divide-m3-outline-variant">
      <InvitationSection applicationId={detail.id} status={detail.status} />
      <JobSummarySection job={detail.job} />
      <SubmittedResumeSection resume={detail.resume} coverLetter={detail.coverLetter} />
      <ScreeningAnswersSection applicationId={detail.id} />
      <TalentPoolConsentSection detail={detail} />
      <ParsedResumeSection resume={detail.resume} />
    </div>
  )
}

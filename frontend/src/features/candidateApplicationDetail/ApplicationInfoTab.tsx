import { InvitationSection } from './InvitationSection'
import { JobSummarySection } from './JobSummarySection'
import { ParsedResumeSection } from './ParsedResumeSection'
import { SubmittedResumeSection } from './SubmittedResumeSection'
import type { ApplicationCandidateDetail } from './types'

// Tab "Thông tin đơn" - bon khoi theo thu tu CO DINH (UI.md muc 4a): Giay moi (chi khi co) -> Tin tuyen
// dung -> Ho so da nop -> CV da trich xuat. divide-y ke vach giua cac khoi dang hien; khoi giay moi an han
// (null) thi khong de lai vach thua. KHONG dung khoi/tab cho FR sau (R-P2).
export function ApplicationInfoTab({ detail }: { detail: ApplicationCandidateDetail }) {
  return (
    <div className="flex flex-col divide-y divide-m3-outline-variant">
      <InvitationSection applicationId={detail.id} status={detail.status} />
      <JobSummarySection job={detail.job} />
      <SubmittedResumeSection resume={detail.resume} coverLetter={detail.coverLetter} />
      <ParsedResumeSection resume={detail.resume} />
    </div>
  )
}

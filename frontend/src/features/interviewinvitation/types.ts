// Khop InterviewInvitationPreviewResponse (backend, package interviewinvitation.dto) - GET
// /hr/applications/{id}/interview-invitation/preview. content da duoc render san tu template
// (placeholder {{candidateName}}/{{jobTitle}}/{{companyName}} da thay the), nhung KHONG co ngay gio
// - HR tu dien khung gio rieng o form gui (xem InterviewInvitationDialog). templateCompanyName la
// ten cong ty da DONG BANG trong template luc tao job, currentCompanyName la ten THAT SU hien tai
// cua cong ty - khac nhau thi companyNameMismatch=true, CHI de canh bao, KHONG tu sua gi ca.
// FR-H12: maxSlots - gioi han khung gio do backend tra, frontend KHONG hardcode (R-S1).
export interface InterviewInvitationPreview {
  subject: string
  content: string
  candidateName: string
  companyNameMismatch: boolean
  templateCompanyName: string
  currentCompanyName: string
  maxSlots: number
}

// FR-H12 (REQUIREMENT muc 4.4) - mot khung gio trong than request. scheduledAt la chuoi ISO-8601 UTC (Instant
// backend) - xem buildScheduledAtIso trong InterviewInvitationDialog.tsx.
export interface InterviewSlotRequest {
  scheduledAt: string
  location: string
}

// Khop InterviewInvitationSendRequest (backend) - than chung cua I2 (gui lan dau) va I3 (gui lai).
// subject/content la NGUYEN VAN HR se gui (co the da sua tu ban preview) - backend KHONG render lai,
// chi luu dung nhung gi gui len.
export interface InterviewInvitationSendRequest {
  slots: InterviewSlotRequest[]
  subject: string
  content: string
}

// Khung gio phia HR. location null chi o du lieu cu chuyen tu V1 (R-S6).
export interface InterviewInvitationSlot {
  id: string
  displayOrder: number
  scheduledAt: string
  location: string | null
}

// Khop InterviewInvitationResponse (backend) - response cua I2, I3 va phan tu cua I1 (chi phia HR).
// active = giay moi hieu luc (R-A1); confirmedSlotId/confirmedAt null = chua chot; slots theo displayOrder.
export interface InterviewInvitationResponse {
  id: string
  applicationId: string
  active: boolean
  subject: string
  renderedContent: string
  sentAt: string
  sentBy: string
  supersededAt: string | null
  confirmedSlotId: string | null
  confirmedAt: string | null
  slots: InterviewInvitationSlot[]
}

// Khop InterviewInvitationListResponse (backend) - I1: giay moi hieu luc truoc, roi giay moi cu theo
// supersededAt giam dan (R-A2).
export interface InterviewInvitationList {
  applicationId: string
  maxSlots: number
  invitations: InterviewInvitationResponse[]
}

// Khop CandidateInterviewInvitationResponse (backend) - GET
// /candidates/applications/{id}/interview-invitation. Chi 5 field, khong co id/applicationId/sentBy
// - ung vien chi can xem lai lich hen, khong can du lieu noi bo do.
// FR-H12: backend da doi hinh dang (I4) - kieu va giao dien phia ung vien doi o dot 6.
export interface CandidateInterviewInvitation {
  scheduledAt: string
  location: string | null
  subject: string
  renderedContent: string
  sentAt: string
}

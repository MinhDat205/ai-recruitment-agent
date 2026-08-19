// Khop InterviewInvitationPreviewResponse (backend, package interviewinvitation.dto) - GET
// /hr/applications/{id}/interview-invitation/preview. content da duoc render san tu template
// (placeholder {{candidateName}}/{{jobTitle}}/{{companyName}} da thay the), nhung KHONG co ngay gio
// - HR tu dien ngay gio rieng o form gui (xem InterviewInvitationDialog). templateCompanyName la
// ten cong ty da DONG BANG trong template luc tao job, currentCompanyName la ten THAT SU hien tai
// cua cong ty - khac nhau thi companyNameMismatch=true, CHI de canh bao, KHONG tu sua gi ca.
export interface InterviewInvitationPreview {
  subject: string
  content: string
  candidateName: string
  companyNameMismatch: boolean
  templateCompanyName: string
  currentCompanyName: string
}

// Khop InterviewInvitationSendRequest (backend) - POST /hr/applications/{id}/interview-invitation.
// subject/content la NGUYEN VAN HR se gui (co the da sua tu ban preview) - backend KHONG render lai,
// chi luu dung nhung gi gui len. scheduledAt la chuoi ISO-8601 UTC (Instant backend) - xem comment
// buildScheduledAtIso trong InterviewInvitationDialog.tsx ve cach quy doi tu input datetime-local.
export interface InterviewInvitationSendRequest {
  scheduledAt: string
  location?: string
  subject: string
  content: string
}

// Khop InterviewInvitationResponse (backend) - tra ve sau khi gui thanh cong (201).
export interface InterviewInvitationResponse {
  id: string
  applicationId: string
  scheduledAt: string
  location: string | null
  subject: string
  renderedContent: string
  sentAt: string
  sentBy: string
}

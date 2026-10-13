import { http } from '../../lib/http'
import type {
  CandidateInterviewInvitation,
  InterviewInvitationList,
  InterviewInvitationPreview,
  InterviewInvitationResponse,
  InterviewInvitationSendRequest,
} from './types'

export async function previewInterviewInvitationRequest(applicationId: string): Promise<InterviewInvitationPreview> {
  const response = await http.get<InterviewInvitationPreview>(
    `/hr/applications/${applicationId}/interview-invitation/preview`,
  )
  return response.data
}

export async function sendInterviewInvitationRequest(
  applicationId: string,
  payload: InterviewInvitationSendRequest,
): Promise<InterviewInvitationResponse> {
  const response = await http.post<InterviewInvitationResponse>(
    `/hr/applications/${applicationId}/interview-invitation`,
    payload,
  )
  return response.data
}

// FR-H12 I3 - gui lai giay moi (don INTERVIEW_INVITED), cung than request voi I2.
export async function resendInterviewInvitationRequest(
  applicationId: string,
  payload: InterviewInvitationSendRequest,
): Promise<InterviewInvitationResponse> {
  const response = await http.post<InterviewInvitationResponse>(
    `/hr/applications/${applicationId}/interview-invitation/resend`,
    payload,
  )
  return response.data
}

// FR-H12 I1 - moi giay moi cua don (phia HR).
export async function listInterviewInvitationsRequest(applicationId: string): Promise<InterviewInvitationList> {
  const response = await http.get<InterviewInvitationList>(`/hr/applications/${applicationId}/interview-invitations`)
  return response.data
}

export async function getInterviewInvitationRequest(applicationId: string): Promise<CandidateInterviewInvitation> {
  const response = await http.get<CandidateInterviewInvitation>(
    `/candidates/applications/${applicationId}/interview-invitation`,
  )
  return response.data
}

import { http } from '../../lib/http'
import type { InterviewInvitationPreview, InterviewInvitationResponse, InterviewInvitationSendRequest } from './types'

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

import { useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query'
import { hrApplicationsKeyPrefix } from '../scoring/queries'
import { getInterviewInvitationRequest, previewInterviewInvitationRequest, sendInterviewInvitationRequest } from './api'
import type { InterviewInvitationSendRequest } from './types'

function interviewInvitationPreviewKey(applicationId: string | undefined) {
  return ['interview-invitation-preview', applicationId]
}

// enabled truyen tu ngoai (dialog dang mo hay khong) - tranh goi preview cho MOI dong trong bang
// ngay khi tai trang, chi fetch dung luc HR bam "Moi phong van".
export function useInterviewInvitationPreviewQuery(applicationId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: interviewInvitationPreviewKey(applicationId),
    queryFn: () => previewInterviewInvitationRequest(applicationId as string),
    enabled: enabled && Boolean(applicationId),
  })
}

// Gui thanh cong doi don sang INTERVIEW_INVITED o backend (ApplicationStatusService.changeStatus,
// goi tu InterviewInvitationService.sendInvitation) - invalidate danh sach cua ca scoring/queries.ts
// de badge trang thai + nut hanh dong cap nhat theo dung trang thai moi. extraInvalidateKeys (FR-H09
// R-A4/R-A5): trang ho so don truyen them query cua trang; mac dinh rong - hanh vi cu giu nguyen.
export function useSendInterviewInvitationMutation(jobId: string, extraInvalidateKeys: QueryKey[] = []) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ applicationId, payload }: { applicationId: string; payload: InterviewInvitationSendRequest }) =>
      sendInterviewInvitationRequest(applicationId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: hrApplicationsKeyPrefix(jobId) })
      extraInvalidateKeys.forEach((queryKey) => queryClient.invalidateQueries({ queryKey }))
    },
  })
}

function interviewInvitationKey(applicationId: string | undefined) {
  return ['interview-invitation', applicationId]
}

// enabled truyen tu ngoai (dialog dang mo hay khong) - cung ly do voi
// useInterviewInvitationPreviewQuery, tranh fetch cho moi dong trong bang khi tai trang.
export function useInterviewInvitationQuery(applicationId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: interviewInvitationKey(applicationId),
    queryFn: () => getInterviewInvitationRequest(applicationId as string),
    enabled: enabled && Boolean(applicationId),
  })
}

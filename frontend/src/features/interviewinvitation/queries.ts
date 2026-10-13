import { useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { hrApplicationsKeyPrefix } from '../scoring/queries'
import {
  getInterviewInvitationRequest,
  listInterviewInvitationsRequest,
  previewInterviewInvitationRequest,
  resendInterviewInvitationRequest,
  sendInterviewInvitationRequest,
} from './api'
import type { InterviewInvitationSendRequest } from './types'

function interviewInvitationPreviewKey(applicationId: string | undefined) {
  return ['interview-invitation-preview', applicationId]
}

// FR-H12 I1 - khoa theo applicationId; applicationDetailInvalidateKeys (trang ho so don) cung lam moi khoa nay.
export function interviewInvitationsKey(applicationId: string) {
  return ['interview-invitations', applicationId]
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

// 4xx la ket qua chot (403/404) - khong thu lai; loi mang/5xx thu lai 1 lan. Cung kieu
// applicationDetail/queries.ts (khong import tu do: applicationDetail/queries import khoa I1 tu file nay).
function retryUnlessClientError(failureCount: number, error: unknown): boolean {
  if (isAxiosError(error)) {
    const status = error.response?.status
    if (status !== undefined && status >= 400 && status < 500) {
      return false
    }
  }
  return failureCount < 1
}

// FR-H12 I1 - tab "Giay moi phong van" cua trang ho so don.
export function useInterviewInvitationsQuery(applicationId: string) {
  return useQuery({
    queryKey: interviewInvitationsKey(applicationId),
    queryFn: () => listInterviewInvitationsRequest(applicationId),
    retry: retryUnlessClientError,
  })
}

// Gui thanh cong doi don sang INTERVIEW_INVITED o backend (ApplicationStatusService.changeStatus,
// goi tu InterviewInvitationService.sendInvitation) - invalidate danh sach cua ca scoring/queries.ts
// de badge trang thai + nut hanh dong cap nhat theo dung trang thai moi. extraInvalidateKeys (FR-H09
// R-A4/R-A5): trang ho so don truyen them query cua trang; mac dinh rong - hanh vi cu giu nguyen.
// FR-H12: lam moi them I1 cua don.
export function useSendInterviewInvitationMutation(jobId: string, extraInvalidateKeys: QueryKey[] = []) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ applicationId, payload }: { applicationId: string; payload: InterviewInvitationSendRequest }) =>
      sendInterviewInvitationRequest(applicationId, payload),
    onSuccess: (_data, { applicationId }) => {
      queryClient.invalidateQueries({ queryKey: hrApplicationsKeyPrefix(jobId) })
      queryClient.invalidateQueries({ queryKey: interviewInvitationsKey(applicationId) })
      extraInvalidateKeys.forEach((queryKey) => queryClient.invalidateQueries({ queryKey }))
    },
    onError: (error, { applicationId }) => {
      // 409 INTERVIEW_INVITATION_CONFLICT (UI.md muc 6) - giay moi da doi o noi khac: lam moi I1.
      if (isAxiosError(error) && error.response?.status === 409) {
        queryClient.invalidateQueries({ queryKey: interviewInvitationsKey(applicationId) })
      }
    },
  })
}

// FR-H12 I3 - gui lai giay moi. Thanh cong: lam moi I1 + cac khoa trang truyen vao (trang thai don KHONG doi, nhung
// lam moi dau trang cho dong bo). 409 (INVITATION_RESEND_NOT_ALLOWED - trang thai don da doi o tab khac;
// INTERVIEW_INVITATION_CONFLICT) - lam moi I1 + dau trang (UI.md muc 6).
export function useResendInterviewInvitationMutation(extraInvalidateKeys: QueryKey[] = []) {
  const queryClient = useQueryClient()
  const refresh = (applicationId: string) => {
    queryClient.invalidateQueries({ queryKey: interviewInvitationsKey(applicationId) })
    extraInvalidateKeys.forEach((queryKey) => queryClient.invalidateQueries({ queryKey }))
  }
  return useMutation({
    mutationFn: ({ applicationId, payload }: { applicationId: string; payload: InterviewInvitationSendRequest }) =>
      resendInterviewInvitationRequest(applicationId, payload),
    onSuccess: (_data, { applicationId }) => refresh(applicationId),
    onError: (error, { applicationId }) => {
      if (isAxiosError(error) && error.response?.status === 409) {
        refresh(applicationId)
      }
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

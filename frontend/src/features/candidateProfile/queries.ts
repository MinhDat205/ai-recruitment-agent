import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getAutofillFromResumeRequest,
  getMyProfileRequest,
  skipOnboardingRequest,
  updateMyProfileRequest,
} from './api'
import type { CandidateProfileRequest } from './types'

const MY_PROFILE_QUERY_KEY = ['candidate-profile', 'me']

// Khong nhu CompanyOwnerQuery: khong co truong hop 404 "chua tao ho so" - CandidateProfileService
// (backend) tu tao ho so trong neu thieu, GET /me luon tra 200.
export function useMyProfileQuery() {
  return useQuery({
    queryKey: MY_PROFILE_QUERY_KEY,
    queryFn: getMyProfileRequest,
  })
}

export function useSaveProfileMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CandidateProfileRequest) => updateMyProfileRequest(payload),
    onSuccess: (data) => {
      queryClient.setQueryData(MY_PROFILE_QUERY_KEY, data)
    },
  })
}

// GET nhung kich hoat boi hanh dong bam nut "Dien tu CV" (R-A2), khong tu dong goi khi mo trang -
// dung useMutation de co san isPending/isError cho UI thay vi useQuery({enabled: false}).
export function useAutofillFromResumeMutation() {
  return useMutation({
    mutationFn: getAutofillFromResumeRequest,
  })
}

// FR-U14 R-O2 - ghi response (onboardingCompletedAt da khac null) vao cache useMyProfileQuery NGAY
// trong onSuccess cua chinh mutation nay, truoc khi component goi mutateAsync() tiep tuc chay code
// sau await (vd navigate) - dung thu tu, tranh RequireCandidateProfileOnboarding doc lai cache cu.
export function useSkipOnboardingMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: skipOnboardingRequest,
    onSuccess: (data) => {
      queryClient.setQueryData(MY_PROFILE_QUERY_KEY, data)
    },
  })
}

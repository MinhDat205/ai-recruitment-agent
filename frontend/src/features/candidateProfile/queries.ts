import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getMyProfileRequest, updateMyProfileRequest } from './api'
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

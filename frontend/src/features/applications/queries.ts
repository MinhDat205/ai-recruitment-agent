import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createApplicationRequest,
  getApplicationHistoryRequest,
  getMyApplicationsRequest,
  withdrawApplicationRequest,
} from './api'

const MY_APPLICATIONS_QUERY_KEY = ['my-applications']

export function useCreateApplicationMutation() {
  return useMutation({
    mutationFn: createApplicationRequest,
  })
}

export function useMyApplicationsQuery() {
  return useQuery({
    queryKey: MY_APPLICATIONS_QUERY_KEY,
    queryFn: getMyApplicationsRequest,
  })
}

export function useApplicationHistoryQuery(id: string | undefined) {
  return useQuery({
    queryKey: ['application-history', id],
    queryFn: () => getApplicationHistoryRequest(id as string),
    enabled: Boolean(id),
  })
}

export function useWithdrawApplicationMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: withdrawApplicationRequest,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: MY_APPLICATIONS_QUERY_KEY })
    },
  })
}

import { useMutation, useQuery } from '@tanstack/react-query'
import { createApplicationRequest, getApplicationHistoryRequest, getMyApplicationsRequest } from './api'

export function useCreateApplicationMutation() {
  return useMutation({
    mutationFn: createApplicationRequest,
  })
}

export function useMyApplicationsQuery() {
  return useQuery({
    queryKey: ['my-applications'],
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

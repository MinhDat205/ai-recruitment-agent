import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getJobDetailRequest, searchJobsRequest } from './api'
import type { JobSearchParams } from './types'

export function useJobsQuery(params: JobSearchParams) {
  return useQuery({
    queryKey: ['public-jobs', params],
    queryFn: () => searchJobsRequest(params),
    placeholderData: keepPreviousData,
  })
}

export function useJobDetailQuery(id: string | undefined) {
  return useQuery({
    queryKey: ['public-job', id],
    queryFn: () => getJobDetailRequest(id as string),
    enabled: Boolean(id),
  })
}

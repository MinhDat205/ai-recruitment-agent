import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getJobDetailRequest, getJobRecommendationsRequest, searchJobsRequest } from './api'
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

// Khong can refetchInterval nhu goi y cai thien CV (F2, poll khi trang thai PENDING/RUNNING):
// cache goi y viec lam da duoc JobRecommendationCacheScheduler sinh san dinh ky o backend,
// endpoint chi doc cache co san - khong co trang thai "dang xu ly" nao o phia candidate can cho.
export function useJobRecommendationsQuery() {
  return useQuery({
    queryKey: ['candidate-job-recommendations'],
    queryFn: getJobRecommendationsRequest,
  })
}

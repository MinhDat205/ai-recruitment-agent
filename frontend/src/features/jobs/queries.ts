import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getJobDetailRequest, getJobRecommendationsRequest, searchJobsRequest } from './api'
import type { JobSearchParams } from './types'

// enabled=false (FR-U07, useJobFilters.canQueryJobs): hoan goi API khi URL co categoryCode/
// locationCode ma danh muc dang tai (chua biet ma co hop le hay khong) - tranh gui ma chua kiem len
// backend. query o trang thai "pending" (chua co data, chua loi) trong luc hoan, JobList doc
// isPending (khong phai isLoading) de hien skeleton dung luc nay.
export function useJobsQuery(params: JobSearchParams, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['public-jobs', params],
    queryFn: () => searchJobsRequest(params),
    placeholderData: keepPreviousData,
    enabled: options?.enabled ?? true,
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

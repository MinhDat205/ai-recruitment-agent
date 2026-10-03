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

// FR-U15 - khong con bo dem (JobRecommendationCacheScheduler cua F1/FR-U04 da xoa), backend tinh
// TRUC TIEP moi lan goi. Khong can refetchInterval: trang thai PREPARING la tam thoi (cho embedding
// tinh xong o lan poll sau cua scheduler khac), nguoi dung tai lai trang se thay cap nhat, khong
// can tu poll lien tuc o day.
export function useJobRecommendationsQuery() {
  return useQuery({
    queryKey: ['candidate-job-recommendations'],
    queryFn: getJobRecommendationsRequest,
  })
}

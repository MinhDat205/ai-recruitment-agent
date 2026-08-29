import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createScoringRunRequest } from '../scoring/api'
import { getScoringRunAuditRequest, listCriteriaNamesRequest, searchCandidatesRequest } from './api'
import type { CandidateSearchParams } from './types'

const CANDIDATES_KEY_PREFIX = 'candidates'

function candidatesKey(params: CandidateSearchParams) {
  return [CANDIDATES_KEY_PREFIX, params]
}

// Dung de invalidate sau khi tao luot cham moi (khong phu thuoc filter/trang dang xem) - mau
// hrApplicationsKeyPrefix cua features/scoring/queries.ts.
export function candidatesKeyPrefix() {
  return [CANDIDATES_KEY_PREFIX]
}

// keepPreviousData: giu trang cu hien thi trong luc trang moi dang tai (doi filter/page khong
// giat man hinh ve trang trong) - mau y het useHrJobsQuery (features/jobs/ownerQueries.ts).
export function useCandidatesQuery(params: CandidateSearchParams) {
  return useQuery({
    queryKey: candidatesKey(params),
    queryFn: () => searchCandidatesRequest(params),
    placeholderData: keepPreviousData,
  })
}

// Khong nhan jobId (trang /hr/candidates xem toan cong ty, khong gan voi mot job) - khac
// useCreateScoringRunMutation cua features/scoring/queries.ts. Invalidate CA tien to (khong chi
// bien the filter/trang dang xem) de moi bien the cache deu cap nhat.
export function useCreateScoringRunMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (applicationId: string) => createScoringRunRequest(applicationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: candidatesKeyPrefix() })
    },
  })
}

const CRITERIA_NAMES_KEY = ['candidate-criteria-names']

export function useCriteriaNamesQuery() {
  return useQuery({
    queryKey: CRITERIA_NAMES_KEY,
    queryFn: listCriteriaNamesRequest,
  })
}

function scoringRunAuditKey(applicationId: string | undefined) {
  return ['scoring-run-audit', applicationId]
}

export function useScoringRunAuditQuery(applicationId: string | undefined) {
  return useQuery({
    queryKey: scoringRunAuditKey(applicationId),
    queryFn: () => getScoringRunAuditRequest(applicationId as string),
    enabled: Boolean(applicationId),
  })
}

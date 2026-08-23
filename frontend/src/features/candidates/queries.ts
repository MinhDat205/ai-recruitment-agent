import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getScoringRunAuditRequest, listCriteriaNamesRequest, searchCandidatesRequest } from './api'
import type { CandidateSearchParams } from './types'

function candidatesKey(params: CandidateSearchParams) {
  return ['candidates', params]
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

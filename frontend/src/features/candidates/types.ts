import type { ApplicationStatus } from '../applications/types'
import type { ParseStatus } from '../resumes/types'
import type { CriterionScoreItem, ScoringRunStatus } from '../scoring/types'

// Khop ApplicationSearchItemResponse (backend, F3/FR-H08) - GET /hr/candidates. KHONG co field
// rank: FR-H05 chi dinh nghia xep hang trong PHAM VI MOT chien dich tuyen dung (mot job), khong co
// khai niem rank xuyen nhieu job. totalScore la BigDecimal, co the null khi don chua co luot DONE
// nao - KHONG suy dien gia tri thay the (cung quy uoc voi ApplicationHrListItem cua D3/D4).
export interface CandidateSearchItem {
  id: string
  jobId: string
  jobTitle: string
  candidateName: string
  resumeParseStatus: ParseStatus
  appliedAt: string
  status: ApplicationStatus
  latestScoringRunId: string | null
  totalScore: number | null
}

// Khop PageResponse<ApplicationSearchItemResponse> (backend, common/dto). Cung hinh dang voi
// PageResponse cua HrJobListParams (features/jobs) - khong dinh nghia kieu phan trang moi.
export interface CandidateSearchPage {
  items: CandidateSearchItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// Tham so gui len GET /hr/candidates. criterionName/minCriterionScore phai di CUNG NHAU - backend
// (ApplicationSearchService.validateFilters) tra 400 neu chi co mot, CandidatesFilterBar phai chan
// truoc khi goi API (khong doi loi 400 roi moi bao HR).
export interface CandidateSearchParams {
  jobId?: string
  status?: ApplicationStatus
  minTotalScore?: number
  maxTotalScore?: number
  criterionName?: string
  minCriterionScore?: number
  page?: number
  size?: number
}

// Khop ScoringRunAuditItemResponse.ExplanationMeta (backend) - CHI model/promptVersion/generatedAt
// (log ky thuat/lineage), KHONG chua summary/strengths/weaknesses (noi dung bao cao da co san o
// Sheet cua ApplicationsTab, khong lap lai o day).
export interface ScoringRunAuditExplanationMeta {
  model: string
  promptVersion: string
  generatedAt: string
}

// Khop ScoringRunAuditItemResponse (backend, F3/FR-H08) - GET
// /hr/candidates/{applicationId}/audit/scoring-runs. Moi luot cham cua MOT don, phuc vu audit/doi
// chieu tuan thu - KHAC HAN ScoringRun (features/scoring/types.ts, dung cho FE poll tien do D2),
// hai kieu/endpoint tach rieng co chu dinh.
export interface ScoringRunAuditItem {
  scoringRunId: string
  status: ScoringRunStatus
  totalScore: number | null
  model: string | null
  promptVersion: string | null
  tokenUsage: number | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  criterionScores: CriterionScoreItem[]
  explanation: ScoringRunAuditExplanationMeta | null
}

import type { ParseStatus } from '../resumes/types'

export type ScoringRunStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

// Khop ScoringRunResponse (backend, package scoring.dto) - POST/GET .../scoring-runs. criteriaScored/
// criteriaTotal CHI phuc vu hien thi tien do ("da cham N/M tieu chi"), KHONG suy ra diem so.
export interface ScoringRun {
  id: string
  applicationId: string
  status: ScoringRunStatus
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  errorMessage: string | null
  criteriaScored: number
  criteriaTotal: number
}

// Khop ApplicationHrListItemResponse.CriterionScoreItem (backend, D4/FR-H05) - CO Y KHONG co
// evidence (viec cua D4/FR-H06, chua lam) va KHONG co field ten verdict/label/isQualified/passed/
// recommendation nao (CLAUDE.md muc 7). reasoning la van ban AI da sinh tu D2 (KHONG phai sinh moi
// o day) - hien thi nguyen van, khong loc/sua boi code (Q6, ke hoach D3).
export interface CriterionScoreItem {
  criterionNameSnapshot: string
  score: number
  maxScoreSnapshot: number
  weightSnapshot: number
  reasoning: string
}

// Khop ApplicationHrListItemResponse (backend, package jobapplication.dto) - GET
// /hr/jobs/{jobId}/applications. latestScoringRun* CHI phan anh trang thai/tien do cua lot cham
// GAN NHAT (co the dang chay/FAILED) - totalScore/rank/criterionScores doc tu lot DONE MOI NHAT
// (Q5, ke hoach D3), co the la MOT LOT KHAC voi lot dang hien thi tien do. totalScore/rank = null
// khi don chua co lot DONE nao (chua cham, chi toan FAILED, hoac dang cham dang) - KHONG suy dien
// gia tri thay the.
export interface ApplicationHrListItem {
  id: string
  candidateName: string
  resumeParseStatus: ParseStatus
  appliedAt: string
  latestScoringRunId: string | null
  latestScoringRunStatus: ScoringRunStatus | null
  latestScoringRunFinishedAt: string | null
  totalScore: number | null
  rank: number | null
  criterionScores: CriterionScoreItem[]
}

// Khop ApplicationSortOption (backend) - gia tri gui qua tham so ?sort= cua GET .../applications.
// 'total_score,desc' la mac dinh (thu tu hien thi = thu tu xep hang).
export type ApplicationSortOption = 'total_score,desc' | 'applied_at,desc'

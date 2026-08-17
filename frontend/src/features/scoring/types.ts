import type { ParseStatus } from '../resumes/types'

export type ScoringRunStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

// Khop ApplicationHrListItemResponse (backend, package jobapplication.dto) - GET
// /hr/jobs/{jobId}/applications. Co y KHONG co totalScore/rank/diem tung tieu chi - viec cua D3/D4,
// chua co o D2. latestScoringRun* CHI phan anh trang thai/tien do cua lot cham GAN NHAT (neu co),
// khong phai ket qua cham diem.
export interface ApplicationHrListItem {
  id: string
  candidateName: string
  resumeParseStatus: ParseStatus
  appliedAt: string
  latestScoringRunId: string | null
  latestScoringRunStatus: ScoringRunStatus | null
  latestScoringRunFinishedAt: string | null
}

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

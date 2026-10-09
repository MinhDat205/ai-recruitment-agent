import type { ApplicationStatus } from '../applications/types'
import type { ParseStatus } from '../resumes/types'
import type { CriterionScoreItem, Explanation, ExplanationStatus } from '../scoring/types'

// Khop ApplicationHrDetailResponse (backend, E1 - GET /hr/applications/{id}). Dau trang ho so don,
// KHONG chua diem/hang/giai thich (R-D1). coverLetter nguyen van nhu da luu, null khi ung vien khong
// nhap (R-D8). resumeParseError la chuoi "MA: mo ta" da chuan hoa hoac null.
export interface ApplicationHrDetail {
  id: string
  jobId: string
  jobTitle: string
  candidateName: string
  status: ApplicationStatus
  appliedAt: string
  coverLetter: string | null
  resumeParseStatus: ParseStatus
  resumeParseError: string | null
  resumeFileName: string
}

// Khop ApplicationScoresResponse (backend, E3). Moi field null, criterionScores rong khi don chua co
// lot DONE nao. totalScore/rank do Backend tinh (FR-H05), frontend KHONG tinh lai (R-D4).
export interface ApplicationScores {
  scoringRunId: string | null
  scoredAt: string | null
  totalScore: number | null
  rank: number | null
  criterionScores: CriterionScoreItem[]
}

// Khop ApplicationExplanationResponse (backend, E4). scoringRunId/scoredAt trung E3 - cung mot lot
// DONE (R-D6). Moi field null khi don chua co lot DONE nao.
export interface ApplicationExplanation {
  scoringRunId: string | null
  scoredAt: string | null
  explanationStatus: ExplanationStatus | null
  explanation: Explanation | null
}

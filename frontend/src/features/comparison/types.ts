import type { ApplicationStatus } from '../applications/types'
import type { EvidenceEntry, ExplanationPoint, ExplanationStatus } from '../scoring/types'
import type { ScreeningCaptureState, ScreeningQuestionType } from '../screening/types'

// Khop comparison.dto.ApplicationComparisonResponse (backend, FR-H14 C1, REQUIREMENT muc 4.4) - DTO DUY NHAT cua bang
// so sanh (R-B1). Moi mang "theo thu tu columns" co cung do dai voi columns. So BigDecimal cua backend ra JSON la number.
// Khong co truong phan quyet hay danh dau nguoi thang nao (R-W3).

export interface ComparisonColumn {
  applicationId: string
  candidateName: string
  status: ApplicationStatus
  appliedAt: string
  // Luot DONE moi nhat - diem, tieu chi, giai thich cung lay tu luot nay.
  scoringRunId: string
  scoredAt: string | null
  // R-D10 [Q6] - co luot cham moi hon dang chay (PENDING/RUNNING).
  newerRunInProgress: boolean
}

// Gia tri so theo cot; valuesDiffer = khong giong nhau o moi cot (R-W2) - backend tinh, frontend khong tu so.
export interface ComparisonRow {
  values: number[]
  valuesDiffer: boolean
}

// Hang (FR-H05) - cung kieu so nguyen voi rank cua danh sach.
export interface ComparisonIntRow {
  values: number[]
  valuesDiffer: boolean
}

export interface ComparisonCriterionCell {
  criterionName: string
  score: number
  maxScore: number
  weight: number
  reasoning: string
  evidence: EvidenceEntry[]
}

export interface ComparisonCriterionRow {
  // criterionId hoac "name:" + ten (R-C2) - chi de lam React key.
  key: string
  // Ten o cot dau tien co tieu chi (R-C5).
  criterionName: string
  rubricVersionsDiffer: boolean
  valuesDiffer: boolean
  // Theo thu tu columns; null = don nay duoc cham theo phien ban rubric khong co tieu chi nay (R-C4).
  cells: (ComparisonCriterionCell | null)[]
}

// Ca hai null = "Chua co du lieu" (R-D5). years do backend quy doi - frontend khong tu tinh.
export interface ComparisonExperience {
  months: number | null
  years: number | null
}

export interface ComparisonExperienceRow {
  values: ComparisonExperience[]
  valuesDiffer: boolean
}

export interface ComparisonEducationItem {
  degree: string | null
  major: string | null
  school: string | null
  startDate: string | null
  endDate: string | null
}

export interface ComparisonProfileCell {
  parsedDataAvailable: boolean
  skills: string[]
  education: ComparisonEducationItem[]
}

// status chi khac null khi luot DONE chua co giai thich (PENDING dang xu ly / FAILED het so lan thu).
export interface ComparisonExplanationCell {
  summary: string | null
  strengths: ExplanationPoint[]
  weaknesses: ExplanationPoint[]
  status: ExplanationStatus | null
}

export type ScreeningCellState = 'ANSWERED' | 'NOT_ANSWERED' | 'NOT_ASKED' | 'NOT_CAPTURED' | 'NO_QUESTIONS'

export interface ComparisonScreeningCell {
  state: ScreeningCellState
  answerText: string | null
  answerYesNo: boolean | null
}

export interface ComparisonScreeningRow {
  questionType: ScreeningQuestionType
  questionText: string
  options: string[]
  // Chi YES_NO/SINGLE_CHOICE; SHORT_TEXT luon false.
  valuesDiffer: boolean
  cells: ComparisonScreeningCell[]
}

export interface ComparisonScreening {
  states: ScreeningCaptureState[]
  // Theo thu tu columns; null khi don NOT_CAPTURED.
  capturedAt: (string | null)[]
  rows: ComparisonScreeningRow[]
}

export interface ApplicationComparisonResponse {
  jobId: string
  jobTitle: string
  recruitmentCycle: number
  rubricVersionsDiffer: boolean
  // Dung thu tu ids tren URL (R-O1) - frontend khong sap lai.
  columns: ComparisonColumn[]
  totalScore: ComparisonRow
  rank: ComparisonIntRow
  criteria: ComparisonCriterionRow[]
  experience: ComparisonExperienceRow
  profiles: ComparisonProfileCell[]
  explanations: ComparisonExplanationCell[]
  screening: ComparisonScreening
}

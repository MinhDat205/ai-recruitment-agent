import type { ApplicationStatus } from '../applications/types'
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

// Khop ApplicationHrListItemResponse.CriterionScoreItem (backend, D4/FR-H05+FR-H06). reasoning la
// van ban AI da sinh tu D2 (KHONG phai sinh moi o day) - hien thi nguyen van, khong loc/sua boi code
// (Q6, ke hoach D3). evidence la trich xuat NGUYEN VAN tu CV da luu o D2 (KHONG phai AI sinh moi o
// day, xem CLAUDE.md nguyen tac evidence) - mang RONG hop le khi score = 0 (Q2, ke hoach D2), khong
// phai thieu du lieu. KHONG co field ten verdict/label/isQualified/passed/recommendation nao
// (CLAUDE.md muc 7).
export interface CriterionScoreItem {
  criterionNameSnapshot: string
  score: number
  maxScoreSnapshot: number
  weightSnapshot: number
  reasoning: string
  evidence: EvidenceEntry[]
}

// Khop scoring.EvidenceEntry (backend) - [{ "quote": "...", "section": "..." }]. section la mot
// trong 6 khoi cua schema CV D1 (contact/education/experience/skills/certifications/projects) -
// anh xa sang nhan tieng Viet o ExplanationReport/CriterionScoreBreakdown, khong hien chuoi tho.
export interface EvidenceEntry {
  quote: string
  section: string
}

// Khop scoring.ExplanationPoint (backend, FR-H06) - moi phan tu gan voi DUNG mot criterionName
// thuoc tap tieu chi da cham (Q3, ke hoach D4), khong phai nhan xet chung chung.
export interface ExplanationPoint {
  criterionName: string
  point: string
}

// Khop ApplicationHrListItemResponse.Explanation (backend, FR-H06, D4). summary/strengths/
// weaknesses la LLM sinh (KHONG chua trich dan nguyen van CV - do la viec cua criterionScores.
// evidence rieng); metCriteria/missingCriteria la Java thuan tinh tu score > 0 (KHONG phai LLM
// sinh). KHONG co field verdict/label/isQualified/passed/recommendation nao (CLAUDE.md muc 7).
export interface Explanation {
  summary: string
  strengths: ExplanationPoint[]
  weaknesses: ExplanationPoint[]
  metCriteria: string[]
  missingCriteria: string[]
}

// Khop ApplicationHrListItemResponse.ExplanationStatus (backend) - tin hieu THUAN TUY VE TRANG THAI
// XU LY, khong mang ham y danh gia ung vien. Xem comment day du trong DTO backend.
export type ExplanationStatus = 'PENDING' | 'FAILED'

// Khop ApplicationHrListItemResponse (backend, package jobapplication.dto) - GET
// /hr/jobs/{jobId}/applications. latestScoringRun* CHI phan anh trang thai/tien do cua lot cham
// GAN NHAT (co the dang chay/FAILED) - totalScore/rank/criterionScores/explanation* doc tu lot DONE
// MOI NHAT (Q5, ke hoach D3), co the la MOT LOT KHAC voi lot dang hien thi tien do. totalScore/rank
// = null khi don chua co lot DONE nao (chua cham, chi toan FAILED, hoac dang cham dang) - KHONG suy
// dien gia tri thay the. explanationStatus/explanation cung theo quy uoc do - xem comment DTO backend.
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
  explanationStatus: ExplanationStatus | null
  explanation: Explanation | null
  // FR-H07 (E1, Dot 3) - trang thai don, dung de quyet dinh nut hanh dong nao hop le (Moi phong
  // van/Tu choi/Trung tuyen). Chi la tien dung UI - backend (ApplicationStatusService) moi la chot
  // chan that su cho moi luong chuyen trang thai.
  status: ApplicationStatus
}

// Khop ApplicationSortOption (backend) - gia tri gui qua tham so ?sort= cua GET .../applications.
// 'total_score,desc' la mac dinh (thu tu hien thi = thu tu xep hang).
export type ApplicationSortOption = 'total_score,desc' | 'applied_at,desc'

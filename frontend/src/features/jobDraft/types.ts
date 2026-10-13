// FR-H11 - khop DTO backend (REQUIREMENT muc 4.4). Response J1 KHONG co trong so, diem, model hay phien ban prompt; trong
// so CHI co o CriterionInput (request J2) - do HR nhap (R-W1).
import type { InterviewTemplateOwnerRequest, JobOwnerRequest } from '../jobs/ownerTypes'
import type { ScreeningQuestionRequest, ScreeningQuestionType } from '../screening/types'

export type JobDraftPart = 'JOB' | 'CRITERIA' | 'QUESTIONS'

export const ALL_PARTS: JobDraftPart[] = ['JOB', 'CRITERIA', 'QUESTIONS']

export type PartStatus = 'SUCCEEDED' | 'FAILED'

// SUCCEEDED: data + generationToken khac null. FAILED: error (ma K3) + message (cau co dinh), data/token null.
export interface PartResult<T> {
  status: PartStatus
  data: T | null
  generationToken: string | null
  error: string | null
  message: string | null
}

export type JobDraftField =
  | 'TITLE'
  | 'DESCRIPTION'
  | 'CATEGORY'
  | 'LOCATION'
  | 'EMPLOYMENT_TYPE'
  | 'WORK_MODE'
  | 'SALARY'
  | 'DEADLINE'
  | 'REQUIREMENTS'
  | 'BENEFITS'

export type FieldNeed = 'SAVE' | 'OPEN' | 'OPTIONAL'

// Do backend tinh (R-M1) - frontend khong tu tinh lai quy tac.
export interface MissingField {
  field: JobDraftField
  need: FieldNeed
}

// Ma danh muc, khong nhan - nhan lay tu /public/catalogs.
export interface JobDraft {
  title: string | null
  description: string | null
  requirements: string | null
  benefits: string | null
  categoryCode: string | null
  locationCode: string | null
  employmentType: string | null
  workMode: string | null
  salaryMin: number | null
  salaryMax: number | null
  salaryCurrency: string | null
  deadline: string | null
  missingFields: MissingField[]
}

export interface CriterionSuggestion {
  name: string
  description: string | null
}

export interface CriteriaDraft {
  criteria: CriterionSuggestion[]
}

// Khong co "bat buoc" (HR tu quyet - R-Q1). options rong voi SHORT_TEXT/YES_NO.
export interface QuestionSuggestion {
  questionType: ScreeningQuestionType
  questionText: string
  options: string[]
}

export interface QuestionsDraft {
  questions: QuestionSuggestion[]
}

// J1
export interface JobAiDraftRequest {
  description: string
  parts: JobDraftPart[]
}

// J1 - o cua phan khong duoc yeu cau = null.
export interface JobAiDraftResponse {
  job: PartResult<JobDraft> | null
  criteria: PartResult<CriteriaDraft> | null
  questions: PartResult<QuestionsDraft> | null
}

// J2 - tieu chi da giu; weight do HR nhap.
export interface CriterionInput {
  name: string
  description: string | null
  weight: number
}

// J2 - token gui NGUYEN VAN, frontend khong giai ma (R-K4-2); null = phan do khong dung.
export interface JobAiDraftSaveRequest {
  job: JobOwnerRequest
  benefits: string | null
  interviewTemplate: InterviewTemplateOwnerRequest
  criteria: CriterionInput[]
  screeningQuestions: ScreeningQuestionRequest[]
  jobToken: string | null
  criteriaToken: string | null
  questionsToken: string | null
}

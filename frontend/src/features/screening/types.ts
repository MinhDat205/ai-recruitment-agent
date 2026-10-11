// FR-H10 - kieu du lieu cua S1-S5 (REQUIREMENT muc 4.4). S6 them o dot 5.

export type ScreeningQuestionType = 'SHORT_TEXT' | 'SINGLE_CHOICE' | 'YES_NO'

// options chi gui khi SINGLE_CHOICE (UI.md 4c) - loai khac bo han truong.
export interface ScreeningQuestionRequest {
  questionType: ScreeningQuestionType
  questionText: string
  options?: string[]
  required: boolean
}

// options rong (khong null) voi SHORT_TEXT/YES_NO.
export interface ScreeningQuestionResponse {
  id: string
  questionType: ScreeningQuestionType
  questionText: string
  options: string[]
  required: boolean
  displayOrder: number
}

// maxQuestions lay tu backend, frontend khong tu hardcode 10.
export interface ScreeningQuestionListResponse {
  jobId: string
  revision: number
  maxQuestions: number
  questions: ScreeningQuestionResponse[]
}

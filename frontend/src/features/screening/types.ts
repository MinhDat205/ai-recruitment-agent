// FR-H10 - kieu du lieu cua S1-S6 (REQUIREMENT muc 4.4).

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

// S6 - trang thai do backend tinh (R-A3); frontend KHONG tu suy tu items.length.
export type ScreeningCaptureState = 'NOT_CAPTURED' | 'NO_QUESTIONS' | 'CAPTURED'

// answerText: SHORT_TEXT hoac chu cua phuong an da chon; answerYesNo: YES_NO; null = ung vien khong tra loi.
export interface ApplicationScreeningItem {
  displayOrder: number
  questionType: ScreeningQuestionType
  questionText: string
  options: string[]
  required: boolean
  answerText: string | null
  answerYesNo: boolean | null
}

// capturedAt null khi NOT_CAPTURED; items rong tru khi CAPTURED.
export interface ApplicationScreeningResponse {
  applicationId: string
  state: ScreeningCaptureState
  capturedAt: string | null
  items: ApplicationScreeningItem[]
}

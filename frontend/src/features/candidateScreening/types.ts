import type { ScreeningQuestionResponse } from '../screening/types'

// FR-U09 F1 - GET /api/candidates/jobs/{jobId}/screening-questions. questions dung lai kieu cua FR-H10 (khong co truong
// nao chi danh cho HR); khong co maxQuestions.
export interface ScreeningForm {
  jobId: string
  revision: number
  questions: ScreeningQuestionResponse[]
}

// FR-U09 F2 - mot cau tra loi gui kem don. answerText: SHORT_TEXT hoac chu cua phuong an; answerYesNo: YES_NO.
export interface ScreeningAnswerInput {
  questionId: string
  answerText: string | null
  answerYesNo: boolean | null
}

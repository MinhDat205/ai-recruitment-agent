import { http } from '../../lib/http'
import type {
  ApplicationScreeningResponse,
  ScreeningQuestionListResponse,
  ScreeningQuestionRequest,
  ScreeningQuestionResponse,
} from './types'

function questionsUrl(jobId: string): string {
  return `/hr/jobs/${jobId}/screening-questions`
}

// S1
export async function getScreeningQuestionsRequest(jobId: string): Promise<ScreeningQuestionListResponse> {
  const response = await http.get<ScreeningQuestionListResponse>(questionsUrl(jobId))
  return response.data
}

// S2 - them vao cuoi danh sach.
export async function addScreeningQuestionRequest(
  jobId: string,
  payload: ScreeningQuestionRequest,
): Promise<ScreeningQuestionResponse> {
  const response = await http.post<ScreeningQuestionResponse>(questionsUrl(jobId), payload)
  return response.data
}

// S3
export async function updateScreeningQuestionRequest(
  jobId: string,
  questionId: string,
  payload: ScreeningQuestionRequest,
): Promise<ScreeningQuestionResponse> {
  const response = await http.put<ScreeningQuestionResponse>(`${questionsUrl(jobId)}/${questionId}`, payload)
  return response.data
}

// S4 - xoa mem, 204 khong co body.
export async function deleteScreeningQuestionRequest(jobId: string, questionId: string): Promise<void> {
  await http.delete(`${questionsUrl(jobId)}/${questionId}`)
}

// S5 - gui TOAN BO thu tu moi; backend tra 400 INVALID_SCREENING_ORDER neu tap id khac danh sach hien tai.
export async function reorderScreeningQuestionsRequest(
  jobId: string,
  questionIds: string[],
): Promise<ScreeningQuestionListResponse> {
  const response = await http.put<ScreeningQuestionListResponse>(`${questionsUrl(jobId)}/order`, { questionIds })
  return response.data
}

// S6 - ban chup cau tra loi cua mot don (chi doc).
export async function getApplicationScreeningRequest(applicationId: string): Promise<ApplicationScreeningResponse> {
  const response = await http.get<ApplicationScreeningResponse>(`/hr/applications/${applicationId}/screening-answers`)
  return response.data
}

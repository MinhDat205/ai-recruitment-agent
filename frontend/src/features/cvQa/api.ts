import { http } from '../../lib/http'
import type { CvQaRequest, CvQaResponse } from './types'

// FR-C08 dung K3 nguyen trang (R-A2): backend cho toi da 2 lan x 15 s nhu FR-C07; trinh duyet doi 35 s roi bao loi
// mang (khuon features/messageDraft/api.ts). Chi dat cho RIENG hai request nay - lib/http.ts khong co timeout chung.
const CV_QA_REQUEST_TIMEOUT_MS = 35_000

// Q1 - HR hoi ve CV da nop vao don. Khong luu gi o backend (R-N1).
export async function askHrCvQuestion(applicationId: string, question: string): Promise<CvQaResponse> {
  const body: CvQaRequest = { question }
  const response = await http.post<CvQaResponse>(`/hr/applications/${applicationId}/cv-qa`, body, {
    timeout: CV_QA_REQUEST_TIMEOUT_MS,
  })
  return response.data
}

// Q2 - ung vien hoi ve CV cua chinh minh (theo CV, khong theo don - Q9).
export async function askCandidateCvQuestion(resumeId: string, question: string): Promise<CvQaResponse> {
  const body: CvQaRequest = { question }
  const response = await http.post<CvQaResponse>(`/candidates/resumes/${resumeId}/cv-qa`, body, {
    timeout: CV_QA_REQUEST_TIMEOUT_MS,
  })
  return response.data
}

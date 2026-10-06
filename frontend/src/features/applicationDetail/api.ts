import { http } from '../../lib/http'
import type { ApplicationHistoryEntry } from '../applications/types'
import type { ResumeParsedDataResponse } from '../resumes/types'
import type { ApplicationExplanation, ApplicationHrDetail, ApplicationScores } from './types'

// FR-H09 - chi goi endpoint phia HR (/api/hr/applications/{id}/...). KHONG goi endpoint phia ung vien
// (bi 403 o filter chain va sai nguon du lieu). Kiem quyen o backend (R-Q1..R-Q3).

// E1 - dau trang.
export async function getApplicationHrDetailRequest(applicationId: string): Promise<ApplicationHrDetail> {
  const response = await http.get<ApplicationHrDetail>(`/hr/applications/${applicationId}`)
  return response.data
}

// E2 - CV da trich xuat cua dung CV da nop vao don (R-D2). Chi goi khi resumeParseStatus = DONE (R-T3).
export async function getApplicationParsedResumeRequest(applicationId: string): Promise<ResumeParsedDataResponse> {
  const response = await http.get<ResumeParsedDataResponse>(`/hr/applications/${applicationId}/resume/parsed`)
  return response.data
}

// E3 - diem cua lot DONE moi nhat + hang.
export async function getApplicationScoresRequest(applicationId: string): Promise<ApplicationScores> {
  const response = await http.get<ApplicationScores>(`/hr/applications/${applicationId}/scores`)
  return response.data
}

// E4 - giai thich cua cung lot voi E3.
export async function getApplicationExplanationRequest(applicationId: string): Promise<ApplicationExplanation> {
  const response = await http.get<ApplicationExplanation>(`/hr/applications/${applicationId}/explanation`)
  return response.data
}

// E5 - lich su trang thai, cu truoc moi sau (R-D7).
export async function getApplicationHrHistoryRequest(applicationId: string): Promise<ApplicationHistoryEntry[]> {
  const response = await http.get<ApplicationHistoryEntry[]>(`/hr/applications/${applicationId}/history`)
  return response.data
}

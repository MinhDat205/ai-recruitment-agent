import { isAxiosError } from 'axios'
import { http } from '../../lib/http'
import type { CvImprovementSuggestion, Resume, ResumeParsedDataResponse } from './types'

export async function listResumesRequest(): Promise<Resume[]> {
  const response = await http.get<Resume[]>('/candidates/resumes')
  return response.data
}

export async function uploadResumeRequest(file: File, versionLabel?: string): Promise<Resume> {
  const formData = new FormData()
  formData.append('file', file)
  if (versionLabel) {
    formData.append('versionLabel', versionLabel)
  }
  // KHONG tu set header Content-Type: axios/trinh duyet tu them "multipart/form-data; boundary=..."
  // dung khi thay body la FormData. Tu set thu cong se mat boundary va backend khong parse duoc.
  const response = await http.post<Resume>('/candidates/resumes', formData)
  return response.data
}

export async function setPrimaryResumeRequest(id: string): Promise<Resume> {
  const response = await http.patch<Resume>(`/candidates/resumes/${id}/primary`)
  return response.data
}

// responseType 'blob' bat buoc: endpoint yeu cau dang nhap, con the` <a href> thuong khong gan
// duoc header Authorization. Ham nay chi tai du lieu ve, viec kich hoat download nam o UI.
export async function downloadResumeRequest(id: string): Promise<Blob> {
  const response = await http.get(`/candidates/resumes/${id}/download`, { responseType: 'blob' })
  return response.data
}

// 404 (RESUME_PARSED_DATA_NOT_FOUND) nghia la CV chua parse xong (PENDING/PROCESSING/FAILED) -
// day la trang thai hop le, khong phai loi he thong, nen tra ve null thay vi nem loi. Loi khac
// (401, 500...) van nem tiep cho caller xu ly nhu binh thuong.
export async function getParsedResumeRequest(id: string): Promise<ResumeParsedDataResponse | null> {
  try {
    const response = await http.get<ResumeParsedDataResponse>(`/candidates/resumes/${id}/parsed`)
    return response.data
  } catch (err) {
    if (isAxiosError(err) && err.response?.status === 404) {
      return null
    }
    throw err
  }
}

// Idempotent o backend (CvImprovementSuggestionService.requestSuggestions) - goi lai nhieu lan
// khong tao them request/khong goi LLM them, luon tra ve trang thai hien hanh.
export async function requestCvImprovementRequest(resumeId: string): Promise<CvImprovementSuggestion> {
  const response = await http.post<CvImprovementSuggestion>(
    `/candidates/resumes/${resumeId}/improvement-suggestions`,
  )
  return response.data
}

export async function getCvImprovementRequest(resumeId: string): Promise<CvImprovementSuggestion> {
  const response = await http.get<CvImprovementSuggestion>(
    `/candidates/resumes/${resumeId}/improvement-suggestions`,
  )
  return response.data
}

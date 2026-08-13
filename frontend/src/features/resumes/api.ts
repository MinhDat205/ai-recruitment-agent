import { http } from '../../lib/http'
import type { Resume } from './types'

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

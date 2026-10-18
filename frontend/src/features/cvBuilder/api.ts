import { isAxiosError } from 'axios'
import { http } from '../../lib/http'
import type { Resume } from '../resumes/types'
import type {
  CvBuilderDocumentResponse,
  CvBuilderRequest,
  CvRephraseRequest,
  CvRephraseResponse,
} from './types'

// B4 dung K3 nguyen trang (R-A3): backend cho toi da 2 lan x 15 s nhu FR-C07; trinh duyet doi 35 s roi bao loi mang
// (khuon features/messageDraft/api.ts). Chi dat cho RIENG request nay - lib/http.ts khong co timeout chung.
const REPHRASE_REQUEST_TIMEOUT_MS = 35_000

// B1 - 201 ResumeResponse cua CV vua tao (ca khi "sua": luon CV moi - R-W6).
export async function createBuilderResume(request: CvBuilderRequest): Promise<Resume> {
  const response = await http.post<Resume>('/candidates/cv-builder/resumes', request)
  return response.data
}

// B2 - form, mau, ten phien ban da luu. 404 RESUME_NOT_FOUND / 409 CV_NOT_FROM_BUILDER nem tiep cho trang xu ly.
export async function getBuilderDocument(resumeId: string): Promise<CvBuilderDocumentResponse> {
  const response = await http.get<CvBuilderDocumentResponse>(`/candidates/cv-builder/resumes/${resumeId}`)
  return response.data
}

// B3 - PDF xem truoc, khong luu (R-B3). responseType 'blob' nen loi cung ve dang Blob: doc Blob.text() ra JSON
// {error, message} va gan lai vao response.data de extractErrorMessage hien dung cau backend (L13 - loi glyph va R-F12
// chi backend bao duoc). Blob khong phai JSON -> giu nguyen loi (caller dung cau du phong).
export async function previewBuilderResume(request: CvBuilderRequest): Promise<Blob> {
  try {
    const response = await http.post<Blob>('/candidates/cv-builder/preview', request, { responseType: 'blob' })
    return response.data
  } catch (err) {
    if (isAxiosError(err) && err.response && err.response.data instanceof Blob) {
      try {
        const parsed: unknown = JSON.parse(await err.response.data.text())
        if (parsed && typeof parsed === 'object') {
          err.response.data = parsed
        }
      } catch {
        // Than loi khong phai JSON (vd trang loi cua proxy) - de nguyen.
      }
    }
    throw err
  }
}

// B4 - goi y dien dat mot doan (K3). Response chi co doan goi y, backend khong luu gi (R-A5).
export async function rephraseDescription(request: CvRephraseRequest): Promise<CvRephraseResponse> {
  const response = await http.post<CvRephraseResponse>('/candidates/cv-builder/rephrase', request, {
    timeout: REPHRASE_REQUEST_TIMEOUT_MS,
  })
  return response.data
}

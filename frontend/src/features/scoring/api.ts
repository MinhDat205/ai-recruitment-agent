import { http } from '../../lib/http'
import type { Application, ApplicationStatus } from '../applications/types'
import type { ApplicationHrListItem, ApplicationSortOption, ScoringRun } from './types'

export async function listHrApplicationsRequest(
  jobId: string,
  sort: ApplicationSortOption,
): Promise<ApplicationHrListItem[]> {
  const response = await http.get<ApplicationHrListItem[]>(`/hr/jobs/${jobId}/applications`, {
    params: { sort },
  })
  return response.data
}

export async function createScoringRunRequest(applicationId: string): Promise<ScoringRun> {
  const response = await http.post<ScoringRun>(`/hr/applications/${applicationId}/scoring-runs`)
  return response.data
}

// FR-H07 (E1, Dot 3) - doi trang thai don (REJECTED/HIRED). INTERVIEW_INVITED KHONG duoc phep gui
// qua day (backend tu choi 400) - luon phai qua InterviewInvitationDialog/sendInterviewInvitationRequest
// (features/interviewinvitation/api.ts), giong dung ranh gioi da chot o ApplicationStatusController.
// Response khop y het type Application (candidate-facing) da co san - khong dinh nghia lai.
export async function changeApplicationStatusRequest(
  applicationId: string,
  status: ApplicationStatus,
): Promise<Application> {
  const response = await http.patch<Application>(`/hr/applications/${applicationId}/status`, { status })
  return response.data
}

export async function listScoringRunsRequest(applicationId: string): Promise<ScoringRun[]> {
  const response = await http.get<ScoringRun[]>(`/hr/applications/${applicationId}/scoring-runs`)
  return response.data
}

// Backend (ResumeHrController) dung ContentDisposition.attachment().filename(name, UTF_8) - sinh CA
// filename* (RFC 5987, ma hoa UTF-8, giu dung ten co dau tieng Viet/ky tu la) LAN filename= (fallback
// ASCII). Uu tien doc filename* vi giu nguyen van, chi doc filename= khi khong co.
function parseContentDispositionFileName(headerValue: string | undefined, fallback: string): string {
  if (!headerValue) {
    return fallback
  }
  const utf8Match = headerValue.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch {
      // Ten khong giai ma duoc (du lieu header hong) - roi xuong doc filename= ASCII ben duoi.
    }
  }
  const asciiMatch = headerValue.match(/filename="?([^";]+)"?/i)
  return asciiMatch ? asciiMatch[1] : fallback
}

// responseType 'blob' bat buoc, cung ly do voi downloadResumeRequest (features/resumes/api.ts):
// endpoint yeu cau dang nhap qua header Authorization (Bearer token trong localStorage, xem
// lib/http.ts) - the <a href>/window.open thuong KHONG gan duoc header nay vao request, chi axios
// (qua interceptor cua http instance) moi lam duoc. Tra ve ca fileName (doc tu Content-Disposition
// server da set) thay vi doan o client - tranh phai mo rong ApplicationHrListItemResponse chi de
// mang mot field ten file cho rieng nut tai CV.
export async function downloadApplicationResumeRequest(applicationId: string): Promise<{ blob: Blob; fileName: string }> {
  const response = await http.get(`/hr/applications/${applicationId}/resume/download`, { responseType: 'blob' })
  const fileName = parseContentDispositionFileName(response.headers['content-disposition'], 'cv.pdf')
  return { blob: response.data, fileName }
}

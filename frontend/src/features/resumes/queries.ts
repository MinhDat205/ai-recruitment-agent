import { useQuery, useMutation, useQueryClient, type Query } from '@tanstack/react-query'
import {
  getCvImprovementRequest,
  getParsedResumeRequest,
  listResumesRequest,
  requestCvImprovementRequest,
  setPrimaryResumeRequest,
  uploadResumeRequest,
} from './api'
import type { CvImprovementStatus, CvImprovementSuggestion, Resume, ResumeParsedDataResponse } from './types'

const RESUMES_QUERY_KEY = ['resumes', 'mine']

// PENDING/PROCESSING la trang thai tam - poller backend chay moi 5s (app.resume-parsing.poll-
// interval-ms), nen refetch phia FE cung o quanh chu ky do de nguoi dung thay chuyen sang DONE/
// FAILED ma khong phai tu tai lai trang.
const RESUME_LIST_POLL_INTERVAL_MS = 5000

// Nguong de coi mot CV la "ket qua lau" va NGUNG poll - tinh tu resume.uploadedAt (moc server,
// Instant UTC), khong phai tu luc nguoi dung mo trang. "Da cho bao lau" la thuoc tinh cua ban ghi,
// khong phai cua phien duyet web - dung uploadedAt song qua F5/nhieu tab mo song song van cho ra
// cung ket qua tren cung 1 CV. 5 phut: poller quet moi 5s, 1 lan goi LLM (ke ca retry 1 lan khi
// JSON hong) thuong chi mat vai chuc giay - 5 phut du du cho ca truong hop cham nhat (backlog dai,
// nhieu CV cung cho trong 1 dot poll) ma van du ngan de khong bat nguoi dung cho vo nghia neu ban
// ghi thuc su ket vinh vien (gioi han da biet cua D1 - app chet giua chung, khong co reclaim).
export const RESUME_STALLED_THRESHOLD_MS = 5 * 60 * 1000

// uploadedAt la chuoi ISO-8601 UTC (Jackson serialize java.time.Instant mac dinh kem hau to "Z") -
// new Date(iso) parse dung UTC bat ke timezone trinh duyet, roi tru cho Date.now() (cung la UTC
// epoch ms) nen phep so sanh nay dung cung he quy chieu, khong lech gio.
export function isResumeStalled(resume: Resume): boolean {
  if (resume.parseStatus !== 'PENDING' && resume.parseStatus !== 'PROCESSING') {
    return false
  }
  const uploadedAtMs = new Date(resume.uploadedAt).getTime()
  return Date.now() - uploadedAtMs > RESUME_STALLED_THRESHOLD_MS
}

// Chi con "dang xu ly" MA CHUA qua nguong moi tinh la can poll tiep - mot CV da ket qua nguong
// khong con keo refetchInterval chay nua (nguoi dung dung nut "Kiem tra lai" de tu refetch thu
// cong khi can), nhung CV khac trong cung danh sach van con moi/dang xu ly binh thuong thi van
// phai tiep tuc poll cho rieng no.
function hasResumeStillPolling(resumes: Resume[] | undefined): boolean {
  return (resumes ?? []).some(
    (resume) => (resume.parseStatus === 'PENDING' || resume.parseStatus === 'PROCESSING') && !isResumeStalled(resume),
  )
}

export function useResumesQuery() {
  return useQuery({
    queryKey: RESUMES_QUERY_KEY,
    queryFn: listResumesRequest,
    refetchInterval: (query: Query<Resume[]>) =>
      hasResumeStillPolling(query.state.data) ? RESUME_LIST_POLL_INTERVAL_MS : false,
  })
}

export function useResumeParsedDataQuery(resumeId: string, enabled: boolean) {
  return useQuery<ResumeParsedDataResponse | null>({
    queryKey: ['resumes', resumeId, 'parsed'],
    queryFn: () => getParsedResumeRequest(resumeId),
    enabled,
  })
}

export function useUploadResumeMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, versionLabel }: { file: File; versionLabel?: string }) =>
      uploadResumeRequest(file, versionLabel),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: RESUMES_QUERY_KEY })
    },
  })
}

export function useSetPrimaryResumeMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => setPrimaryResumeRequest(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: RESUMES_QUERY_KEY })
    },
  })
}

// Hang so poll RIENG cho trang goi y cai thien CV - KHONG dung chung RESUME_LIST_POLL_INTERVAL_MS
// du cung gia tri 5000ms, vi hai hang so gan voi hai poller backend doc lap
// (app.resume-parsing.poll-interval-ms cua D1 va app.cv-improvement.poll-interval-ms cua F2) -
// doi mot ben trong tuong lai khong keo theo ben con lai.
const CV_IMPROVEMENT_POLL_INTERVAL_MS = 5000

function cvImprovementQueryKey(resumeId: string) {
  return ['resumes', resumeId, 'improvement-suggestions']
}

// PENDING/RUNNING la hai trang thai con dang cho poller backend (CvImprovementScheduler) xu ly -
// mau y het hasResumeStillPolling/isResumeStalled cua D1, nhung don gian hon: khong can nguong
// "cho qua lau" o day, trang chi phuc vu dung MOT resumeId (khong phai danh sach nhieu CV) nen
// nguoi dung tu thay ro dang cho gi, khong can co che tu ngat poll rieng.
function isCvImprovementPending(status: CvImprovementStatus | undefined): boolean {
  return status === 'PENDING' || status === 'RUNNING'
}

export function useCvImprovementQuery(resumeId: string) {
  return useQuery({
    queryKey: cvImprovementQueryKey(resumeId),
    queryFn: () => getCvImprovementRequest(resumeId),
    refetchInterval: (query: Query<CvImprovementSuggestion>) =>
      isCvImprovementPending(query.state.data?.status) ? CV_IMPROVEMENT_POLL_INTERVAL_MS : false,
  })
}

export function useRequestCvImprovementMutation(resumeId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => requestCvImprovementRequest(resumeId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cvImprovementQueryKey(resumeId) })
    },
  })
}

import type { ScreeningAnswerInput } from '../candidateScreening/types'

export type ApplicationStatus = 'PENDING' | 'INTERVIEW_INVITED' | 'HIRED' | 'REJECTED' | 'WITHDRAWN'

// KHONG co recruitmentCycle: backend tu lay tu Job hien tai (FR-U02), khong tin client.
// FR-U09: screeningRevision lay tu F1 (gui ca khi tin co 0 cau, R-F4); answers chi gom cau da tra loi; talentPoolConsent
// la lua chon rieng, tach khoi aiConsent (R-K1).
export interface ApplicationCreateRequest {
  jobId: string
  resumeId: string
  aiConsent: boolean
  coverLetter?: string
  screeningRevision: number
  answers: ScreeningAnswerInput[]
  talentPoolConsent: boolean
}

export interface Application {
  id: string
  jobId: string
  resumeId: string
  recruitmentCycle: number
  status: ApplicationStatus
  aiConsentAt: string
  coverLetter: string | null
  appliedAt: string
  updatedAt: string
}

export interface ApplicationSummary {
  id: string
  jobId: string
  jobTitle: string
  companyName: string
  status: ApplicationStatus
  appliedAt: string
  updatedAt: string
}

export interface ApplicationHistoryEntry {
  id: string
  fromStatus: ApplicationStatus | null
  toStatus: ApplicationStatus
  note: string | null
  changedAt: string
}

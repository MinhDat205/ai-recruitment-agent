export type ApplicationStatus = 'PENDING' | 'INTERVIEW_INVITED' | 'HIRED' | 'REJECTED' | 'WITHDRAWN'

// KHONG co recruitmentCycle: backend tu lay tu Job hien tai (FR-U02), khong tin client.
export interface ApplicationCreateRequest {
  jobId: string
  resumeId: string
  aiConsent: boolean
  coverLetter?: string
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

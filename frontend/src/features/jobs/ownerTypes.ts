export type JobStatus = 'DRAFT' | 'OPEN' | 'PAUSED' | 'CLOSED'

export interface JobOwnerResponse {
  id: string
  companyId: string
  title: string
  description: string
  requirements: string | null
  category: string | null
  location: string | null
  employmentType: string | null
  workMode: string | null
  salaryMin: number | null
  salaryMax: number | null
  salaryCurrency: string | null
  status: JobStatus
  recruitmentCycle: number
  deadline: string | null
  publishedAt: string | null
  deletedAt: string | null
  createdAt: string
  updatedAt: string
  rubricId: string | null
  interviewTemplateId: string | null
}

// Khop JobRequest o backend - dung cho ca PUT /hr/jobs/{id} lan phan "job" cua JobCreateRequest.
export interface JobOwnerRequest {
  title: string
  description: string
  requirements?: string
  category?: string
  location?: string
  employmentType?: string
  workMode?: string
  salaryMin?: number
  salaryMax?: number
  salaryCurrency?: string
  deadline?: string
}

// Khop InterviewTemplateRequest o backend. KHONG co truong ngay gio - xem InterviewTemplate.java
// o backend: FR-H02 quy dinh "o trong" nghia la khong ton tai field nay o tang template, ngay
// gio phong van cu the do HR dien khi moi tung ung vien o nhanh E1 (FR-H07).
export interface InterviewTemplateOwnerRequest {
  subject: string
  body: string
  senderName: string
  senderTitle?: string
  address?: string
}

export interface InterviewTemplateOwnerResponse {
  id: string
  jobId: string
  companyName: string
  subject: string
  body: string
  senderName: string
  senderTitle: string | null
  address: string | null
  createdAt: string
  updatedAt: string
}

// Khop JobCreateRequest o backend - POST /hr/jobs bat buoc gom ca hai phan trong MOT request,
// khong tach thanh 2 request rieng (xem JobOwnerController.create).
export interface JobCreateOwnerRequest {
  job: JobOwnerRequest
  interviewTemplate: InterviewTemplateOwnerRequest
}

export interface HrJobListParams {
  status?: JobStatus
  page?: number
  size?: number
}

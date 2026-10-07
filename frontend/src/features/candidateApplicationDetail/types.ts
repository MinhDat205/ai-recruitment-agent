import type { ApplicationStatus } from '../applications/types'
import type { ParseStatus } from '../resumes/types'

// FR-U08 R-D4 - dung 5 gia tri, backend tinh (frontend KHONG tu suy tu deadline). Tin da xoa mem va tin
// dua ve nhap gop chung UNAVAILABLE.
export type JobAvailability = 'OPEN' | 'EXPIRED' | 'PAUSED' | 'CLOSED' | 'UNAVAILABLE'

// GET /api/candidates/applications/{id} (E1) - khop tung field ApplicationCandidateDetailResponse
// (REQUIREMENT.md muc 4). Khong co diem, hang, tieu chi, giai thich AI, luot cham.
export interface ApplicationCandidateDetail {
  id: string
  status: ApplicationStatus
  appliedAt: string
  updatedAt: string
  coverLetter: string | null
  job: {
    id: string
    title: string
    companyId: string
    companyName: string
    availability: JobAvailability
    categoryCode: string | null
    categoryLabel: string | null
    locationCode: string | null
    locationLabel: string | null
    legacyCategory: string | null
    legacyLocation: string | null
    employmentType: string | null
    workMode: string | null
    salaryMin: number | null
    salaryMax: number | null
    salaryCurrency: string | null
    deadline: string | null
  }
  resume: {
    // = job_applications.resume_id (CV da nop), KHONG phai CV chinh hien tai (R-D2)
    id: string
    fileName: string
    parseStatus: ParseStatus
    parseError: string | null
  }
}

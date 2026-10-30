import type { ApplicationStatus } from '../applications/types'
import type { JobAvailability } from '../jobs/jobAvailability'
import type { ParseStatus } from '../resumes/types'

// FR-U08 R-D4 - kieu JobAvailability (5 gia tri) chuyen ra features/jobs/jobAvailability.ts o FR-U16 R-A5.

// FR-U09 R-K5 - trang thai dong y luu ho so do backend tinh (frontend khong tu suy tu hai moc thoi gian).
export type TalentPoolConsentState = 'NOT_GIVEN' | 'ACTIVE' | 'REVOKED'

// Khoa talentPoolConsent cua E1 va than response F4 (TalentPoolConsentResponse).
export interface TalentPoolConsent {
  state: TalentPoolConsentState
  consentedAt: string | null
  revokedAt: string | null
}

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
  // FR-U09 R-D1 - khoa duy nhat FR-U09 them vao E1.
  talentPoolConsent: TalentPoolConsent
}

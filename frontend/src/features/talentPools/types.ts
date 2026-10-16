import type { ApplicationStatus } from '../applications/types'
import type { TalentPoolConsentState } from '../candidateApplicationDetail/types'

export type { PageResponse } from '../jobs/types'

// FR-H15 P1-P4 (REQUIREMENT muc 4.4). entryCount da loc dong y o backend (R-M6).
export interface TalentPool {
  id: string
  name: string
  entryCount: number
  createdAt: string
}

// P8. KHONG co diem, hang hay trich dan minh chung (R-A2) - xem lai danh gia AI bang cach mo trang ho so don.
export interface TalentPoolEntry {
  applicationId: string
  candidateName: string
  jobId: string
  jobTitle: string
  jobDeleted: boolean
  applicationStatus: ApplicationStatus
  appliedAt: string
  addedAt: string
}

// P6, P7 (hop thoai "Luu vao kho", UI.md 4d). pools = MOI kho chua xoa cua cong ty; contains loc dong y o backend (R-M6)
// nen don khong ACTIVE luon contains = false.
export interface ApplicationTalentPools {
  consentState: TalentPoolConsentState
  pools: { id: string; name: string; contains: boolean }[]
}

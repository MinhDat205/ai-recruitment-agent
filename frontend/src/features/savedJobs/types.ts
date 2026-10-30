import type { ApplicationStatus } from '../applications/types'
import type { JobAvailability } from '../jobs/jobAvailability'
import type { CompanyRef, JobSummary } from '../jobs/types'

// FR-U16 E3 - khop tung field SavedJobResponse (REQUIREMENT muc 4.3). Khong diem, khong hang, khong do phu hop.
export interface SavedJobApplication {
  id: string
  status: ApplicationStatus
}

export interface SavedJob {
  jobId: string
  title: string
  company: CompanyRef | null
  savedAt: string
  availability: JobAvailability
  // null khi availability = UNAVAILABLE (R-V6)
  summary: JobSummary | null
  // null khi chua co don o chu ky hien tai cua tin (R-V3)
  application: SavedJobApplication | null
}

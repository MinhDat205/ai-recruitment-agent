import type { JobSummary, WorkMode } from '../jobs/types'

// FR-U13 REQUIREMENT muc 4.5 - khop dung DTO backend (jobsearch/dto). Response KHONG co field diem/do tuong dong nao.

export interface JobSearchRequest {
  query: string
}

// R-K1 - DESCRIPTION: xep theo phan mo ta; NEWEST: khong con phan mo ta, sap theo thoi gian dang.
export type JobSearchRanking = 'DESCRIPTION' | 'NEWEST'

// Chi ma danh muc - frontend tu doi ma -> nhan bang danh muc C05 da tai (useCatalogsQuery).
export interface JobSearchInterpretation {
  locationCodes: string[]
  industryCodes: string[]
  // Trieu VND, null = khong co (R-C4).
  salaryMin: number | null
  salaryMax: number | null
  workModes: WorkMode[]
  // Nguyen van con lai cua cau, null = khong co (R-D).
  description: string | null
  personalInfoIgnored: boolean
}

export interface JobSearchItem {
  job: JobSummary
  // R-M - backend tinh san (nhan da doi), frontend chi hien nguyen.
  matchedConditions: string[]
  // R-K4 - false: viec chua co embedding, nam cuoi danh sach sau cau ngan cach.
  rankedByDescription: boolean
}

export interface JobSearchResponse {
  interpretation: JobSearchInterpretation
  ranking: JobSearchRanking
  // Toi da 50, dung thu tu xep cua backend.
  items: JobSearchItem[]
}

// Mot the dieu kien trong khoi AI (chi o frontend) - dung khi xoa the truoc khi chuyen sang bo loc U07. Luong la MOT
// the (gom ca min va max).
export type JobSearchCondition =
  | { kind: 'location'; code: string }
  | { kind: 'industry'; code: string }
  | { kind: 'salary' }
  | { kind: 'workMode'; code: WorkMode }

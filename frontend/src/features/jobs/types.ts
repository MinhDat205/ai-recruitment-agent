export interface CompanyRef {
  id: string
  name: string
  logoUrl: string | null
}

export interface JobSummary {
  id: string
  title: string
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
  publishedAt: string | null
  company: CompanyRef | null
}

export interface JobDetail {
  id: string
  title: string
  description: string
  requirements: string | null
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
  publishedAt: string | null
  createdAt: string
  company: CompanyRef | null
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// FR-U07 R-F2: tap gia tri kin, khop dung enum backend (JobSortOption, R-W1, R-T2).
export type WorkMode = 'ONSITE' | 'HYBRID' | 'REMOTE'
export type PostedWithin = 'LAST_24H' | 'LAST_7D' | 'LAST_30D'
export type JobSort = 'NEWEST' | 'SALARY_DESC'

// FR-U15 R-S - khop dung enum backend JobRecommendationStatus/JobRecommendationSource.
export type JobRecommendationStatus = 'READY' | 'NO_DATA' | 'PREPARING' | 'NO_RESULT'
export type JobRecommendationSource = 'CV' | 'PROFILE' | 'DESIRES'

// R-M1 - matchedConditions do BACKEND tinh san, frontend chi hien nguyen, khong tu so job voi
// mong muon. KHONG co field diem so nao (R-S3).
export interface JobRecommendationItem {
  job: JobSummary
  matchedConditions: string[]
}

// Thay the hoan toan kieu cu (List<JobSummary> doc tu bo dem F1/FR-U04 da xoa) - R-S.
export interface JobRecommendationResponse {
  status: JobRecommendationStatus
  source: JobRecommendationSource | null
  items: JobRecommendationItem[]
}

// R-F1: khong con category/location (chuoi tu do, cu) - thanh loc moi chi gui *Code theo danh muc
// C05. Backend van giu category/location cho URL/test cu, chi frontend khong gui nua.
// FR-U15 R-H3 - categoryCode/locationCode doi tu 1 gia tri sang danh sach (toi da 3, R-H4).
export interface JobSearchParams {
  keyword?: string
  categoryCode?: string[]
  locationCode?: string[]
  salaryMin?: number
  salaryMax?: number
  hideUnlisted?: boolean
  workMode?: WorkMode[]
  postedWithin?: PostedWithin
  sort?: JobSort
  page?: number
  size?: number
}

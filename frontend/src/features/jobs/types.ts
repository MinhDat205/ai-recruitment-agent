export interface CompanyRef {
  id: string
  name: string
  logoUrl: string | null
}

export interface JobSummary {
  id: string
  title: string
  category: string | null
  location: string | null
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
  category: string | null
  location: string | null
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

export interface JobSearchParams {
  keyword?: string
  location?: string
  category?: string
  page?: number
  size?: number
}

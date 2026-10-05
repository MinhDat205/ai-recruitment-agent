import type { CatalogItem } from '../catalog/types'

export interface CandidateProfileRequest {
  headline?: string
  location?: string
  currentTitle?: string
  yearsExperience?: number
  dateOfBirth?: string
  // FR-U14 R-V1 - PUT la thay the toan bo (R-V3): form luon gui du 4 field mang + luong/bio.
  desiredIndustryCodes: string[]
  desiredLocationCodes: string[]
  desiredWorkModes: string[]
  skills: string[]
  desiredSalaryMinMillions?: number
  bio?: string
}

export interface CandidateProfileResponse {
  id: string
  headline: string | null
  location: string | null
  currentTitle: string | null
  yearsExperience: number | null
  dateOfBirth: string | null
  createdAt: string
  updatedAt: string
  // FR-U14 muc 4 - da tra nhan san qua CatalogRegistry (giong ResumeParsedDataResponse.industry).
  desiredIndustries: CatalogItem[]
  desiredLocations: CatalogItem[]
  desiredWorkModes: string[]
  skills: string[]
  desiredSalaryMinMillions: number | null
  bio: string | null
  onboardingCompletedAt: string | null
}

// FR-U14 R-A1 - ket qua "Dien tu CV", doc xac dinh tu CV chinh da DONE, khong goi AI.
export interface ResumeAutofillResponse {
  currentTitle: string | null
  skills: string[]
  yearsExperience: number | null
}

export interface CandidateProfileRequest {
  headline?: string
  location?: string
  currentTitle?: string
  yearsExperience?: number
  dateOfBirth?: string
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
}

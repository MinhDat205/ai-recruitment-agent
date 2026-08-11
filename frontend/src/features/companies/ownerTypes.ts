export interface CompanyOwnerRequest {
  name: string
  description?: string
  companySize?: string
  industry?: string
  website?: string
  contactEmail?: string
  contactPhone?: string
  address?: string
}

export interface CompanyOwnerResponse {
  id: string
  ownerId: string
  name: string
  logoUrl: string | null
  description: string | null
  companySize: string | null
  industry: string | null
  website: string | null
  contactEmail: string | null
  contactPhone: string | null
  address: string | null
  createdAt: string
  updatedAt: string
}

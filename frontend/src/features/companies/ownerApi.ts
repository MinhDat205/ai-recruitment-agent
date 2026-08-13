import { http } from '../../lib/http'
import type { CompanyOwnerRequest, CompanyOwnerResponse } from './ownerTypes'

export async function getMyCompanyRequest(): Promise<CompanyOwnerResponse> {
  const response = await http.get<CompanyOwnerResponse>('/hr/companies/me')
  return response.data
}

export async function createCompanyRequest(payload: CompanyOwnerRequest): Promise<CompanyOwnerResponse> {
  const response = await http.post<CompanyOwnerResponse>('/hr/companies', payload)
  return response.data
}

export async function updateCompanyRequest(
  id: string,
  payload: CompanyOwnerRequest,
): Promise<CompanyOwnerResponse> {
  const response = await http.put<CompanyOwnerResponse>(`/hr/companies/${id}`, payload)
  return response.data
}

export async function uploadLogoRequest(id: string, file: File): Promise<CompanyOwnerResponse> {
  const formData = new FormData()
  formData.append('file', file)
  // KHONG tu set header Content-Type: axios/trinh duyet tu them "multipart/form-data; boundary=..."
  // dung khi thay body la FormData. Tu set thu cong se mat boundary va backend khong parse duoc.
  const response = await http.post<CompanyOwnerResponse>(`/hr/companies/${id}/logo`, formData)
  return response.data
}

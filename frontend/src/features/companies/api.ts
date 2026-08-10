import { http } from '../../lib/http'
import type { CompanyPublic } from './types'

export async function getCompanyRequest(id: string): Promise<CompanyPublic> {
  const response = await http.get<CompanyPublic>(`/public/companies/${id}`)
  return response.data
}

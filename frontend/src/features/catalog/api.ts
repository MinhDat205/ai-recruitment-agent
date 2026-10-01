import { http } from '../../lib/http'
import type { Catalogs } from './types'

export async function getCatalogsRequest(): Promise<Catalogs> {
  const response = await http.get<Catalogs>('/public/catalogs')
  return response.data
}

import { http } from '../../lib/http'
import type { Application, ApplicationCreateRequest } from './types'

export async function createApplicationRequest(payload: ApplicationCreateRequest): Promise<Application> {
  const response = await http.post<Application>('/candidates/applications', payload)
  return response.data
}

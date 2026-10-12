import { http } from '../../lib/http'
import type { ApplicationScreeningResponse } from '../screening/types'
import type { ScreeningForm } from './types'

// F1 - bo cau hoi dang hoat dong + revision cua tin xem duoc cong khai (404 JOB_NOT_FOUND neu tin khong con nhan don).
export async function getScreeningFormRequest(jobId: string): Promise<ScreeningForm> {
  const response = await http.get<ScreeningForm>(`/candidates/jobs/${jobId}/screening-questions`)
  return response.data
}

// F3 - ban chup cau tra loi cua CHINH don minh (404 APPLICATION_NOT_FOUND neu khong phai don cua minh).
export async function getMyApplicationScreeningRequest(applicationId: string): Promise<ApplicationScreeningResponse> {
  const response = await http.get<ApplicationScreeningResponse>(
    `/candidates/applications/${applicationId}/screening-answers`,
  )
  return response.data
}

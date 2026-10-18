import { http } from '../../lib/http'
import type { JobSearchRequest, JobSearchResponse } from './types'

// S1 goi K3 (LLM) roi embedding: backend te nhat khoang 35 s (REQUIREMENT O4) - trinh duyet doi 40 s roi bao loi mang.
// Chi dat cho RIENG request nay - lib/http.ts khong co timeout chung (khuon features/cvBuilder/api.ts).
const JOB_SEARCH_REQUEST_TIMEOUT_MS = 40_000

// S1 - tim viec bang mo ta. Backend khong luu gi (R-L); moi lan goi tieu mot luot ai-sync (R-I4).
export async function searchJobsByDescription(request: JobSearchRequest): Promise<JobSearchResponse> {
  const response = await http.post<JobSearchResponse>('/candidates/job-search', request, {
    timeout: JOB_SEARCH_REQUEST_TIMEOUT_MS,
  })
  return response.data
}

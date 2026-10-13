import { http } from '../../lib/http'
import type { JobOwnerResponse } from '../jobs/ownerTypes'
import type { JobAiDraftRequest, JobAiDraftResponse, JobAiDraftSaveRequest } from './types'

// FR-H11 J1 - backend cho toi da ~30 s (2 lan x 15 s cua K3, cac phan chay song song); trinh duyet doi 35 s roi bao loi
// mang (UI.md muc 6). Chi dat cho RIENG request nay.
const GENERATE_TIMEOUT_MS = 35_000

// J1 - MOT ham duy nhat, nhan danh sach phan (L9): lan dau gui ca ba phan, "Tao lai"/"Thu lai" mot phan chi gui phan do.
// Moi lan goi = mot request = mot luot ai-sync. Khong goi lap theo tung phan.
export async function generateJobDraftRequest(input: JobAiDraftRequest): Promise<JobAiDraftResponse> {
  const response = await http.post<JobAiDraftResponse>('/hr/job-ai-drafts', input, { timeout: GENERATE_TIMEOUT_MS })
  return response.data
}

// J2 - luu nguyen khoi trong mot transaction o backend; tin luon o trang thai Nhap.
export async function saveJobDraftRequest(input: JobAiDraftSaveRequest): Promise<JobOwnerResponse> {
  const response = await http.post<JobOwnerResponse>('/hr/job-ai-drafts/save', input)
  return response.data
}

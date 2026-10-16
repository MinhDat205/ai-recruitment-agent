import { http } from '../../lib/http'
import type { ApplicationComparisonResponse } from './types'

// FR-H14 C1 - endpoint DUY NHAT cua trang so sanh (R-B1). idsParam gui NGUYEN VAN chuoi tren URL (R-S9): khong bo
// trung, khong cat, khong sap. null (URL khong co tham so ids) -> goi khong kem ids de backend tra 400 R-S8.
export async function getApplicationComparisonRequest(
  jobId: string,
  idsParam: string | null,
): Promise<ApplicationComparisonResponse> {
  const response = await http.get<ApplicationComparisonResponse>(`/hr/jobs/${jobId}/applications/compare`, {
    params: idsParam === null ? undefined : { ids: idsParam },
  })
  return response.data
}

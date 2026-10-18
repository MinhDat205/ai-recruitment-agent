import { http } from '../../lib/http'

// FR-H16 B1 (REQUIREMENT muc 4.3, 4.4). Duong DUY NHAT doi cong tac - khong gui co qua PUT /hr/jobs/{id} (UI.md muc 10).
export interface BlindReviewResponse {
  jobId: string
  enabled: boolean
  // changed_at cua dong luu vet moi nhat; null neu chua tung doi.
  changedAt: string | null
}

export async function updateBlindReviewRequest(jobId: string, enabled: boolean): Promise<BlindReviewResponse> {
  const response = await http.patch<BlindReviewResponse>(`/hr/jobs/${jobId}/blind-review`, { enabled })
  return response.data
}

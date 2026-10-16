import { http } from '../../lib/http'
import type { ApplicationTalentPools, PageResponse, TalentPool, TalentPoolEntry } from './types'

// FR-H15 - REQUIREMENT muc 4.3. ownerId lay tu JWT o backend; frontend chi gui id tren duong dan.
const BASE = '/hr/talent-pools'

// P1 - khong phan trang (R-K7).
export async function getTalentPoolsRequest(): Promise<TalentPool[]> {
  const response = await http.get<TalentPool[]>(BASE)
  return response.data
}

// P2
export async function createTalentPoolRequest(name: string): Promise<TalentPool> {
  const response = await http.post<TalentPool>(BASE, { name })
  return response.data
}

// P3
export async function getTalentPoolRequest(poolId: string): Promise<TalentPool> {
  const response = await http.get<TalentPool>(`${BASE}/${poolId}`)
  return response.data
}

// P4
export async function renameTalentPoolRequest(poolId: string, name: string): Promise<TalentPool> {
  const response = await http.patch<TalentPool>(`${BASE}/${poolId}`, { name })
  return response.data
}

// P5 - xoa mem, 204 khong co body.
export async function deleteTalentPoolRequest(poolId: string): Promise<void> {
  await http.delete(`${BASE}/${poolId}`)
}

// P8 - page 0-based; size de backend tu chon mac dinh (10).
export async function getTalentPoolEntriesRequest(poolId: string, page: number): Promise<PageResponse<TalentPoolEntry>> {
  const response = await http.get<PageResponse<TalentPoolEntry>>(`${BASE}/${poolId}/entries`, { params: { page } })
  return response.data
}

// P9 - 204 khong co body.
export async function removeTalentPoolEntryRequest(poolId: string, applicationId: string): Promise<void> {
  await http.delete(`${BASE}/${poolId}/entries/${applicationId}`)
}

// P6 - kho cua MOT don + trang thai dong y (hop thoai "Luu vao kho").
export async function getApplicationTalentPoolsRequest(applicationId: string): Promise<ApplicationTalentPools> {
  const response = await http.get<ApplicationTalentPools>(`/hr/applications/${applicationId}/talent-pools`)
  return response.data
}

// P7 - poolIds = TOAN BO tap kho mong muon chua don (R-P1); backend tu tinh them/go.
export async function replaceApplicationTalentPoolsRequest(
  applicationId: string,
  poolIds: string[],
): Promise<ApplicationTalentPools> {
  const response = await http.put<ApplicationTalentPools>(`/hr/applications/${applicationId}/talent-pools`, {
    poolIds,
  })
  return response.data
}

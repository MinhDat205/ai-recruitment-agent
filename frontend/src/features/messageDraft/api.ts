import { http } from '../../lib/http'
import type { DraftScenariosResponse, DraftSide, MessageDraftRequest, MessageDraftResponse } from './types'

// FR-C07 A2 - backend cho toi da 2 lan x 15 s (R-K3-1); trinh duyet doi 35 s roi bao loi mang (UI.md muc 6). Chi dat
// cho RIENG request nay - lib/http.ts khong co timeout chung.
const DRAFT_REQUEST_TIMEOUT_MS = 35_000

// Tien to chon theo side, KHONG co endpoint dung chung hai vai tro.
function draftPath(side: DraftSide, applicationId: string): string {
  return side === 'hr'
    ? `/hr/applications/${applicationId}/messages/ai-draft`
    : `/candidates/applications/${applicationId}/messages/ai-draft`
}

// A1
export async function getDraftScenariosRequest(
  side: DraftSide,
  applicationId: string,
): Promise<DraftScenariosResponse> {
  const response = await http.get<DraftScenariosResponse>(`${draftPath(side, applicationId)}/scenarios`)
  return response.data
}

// A2 - chi tra ban nhap, KHONG gui tin (R-D2): gui tin chi qua nut "Gui" cua MessageComposer (M2).
export async function createMessageDraftRequest(
  side: DraftSide,
  applicationId: string,
  input: MessageDraftRequest,
): Promise<MessageDraftResponse> {
  const response = await http.post<MessageDraftResponse>(draftPath(side, applicationId), input, {
    timeout: DRAFT_REQUEST_TIMEOUT_MS,
  })
  return response.data
}

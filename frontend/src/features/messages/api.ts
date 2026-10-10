import { http } from '../../lib/http'
import type {
  ConversationCandidate,
  ConversationHr,
  ConversationPage,
  Message,
  MessageSide,
  MessageThread,
} from './types'

// FR-C06 M1-M4 theo don. Tien to chon theo side (R-C2), KHONG co endpoint dung chung hai vai tro.
function threadPath(side: MessageSide, applicationId: string): string {
  return side === 'hr'
    ? `/hr/applications/${applicationId}/messages`
    : `/candidates/applications/${applicationId}/messages`
}

// M1
export async function getMessageThreadRequest(side: MessageSide, applicationId: string): Promise<MessageThread> {
  const response = await http.get<MessageThread>(threadPath(side, applicationId))
  return response.data
}

// M2 - FormData (multipart/form-data): field "body" (tuy chon) va "file" (tuy chon). Noi dung gui NGUYEN VAN,
// khong trim/escape - backend chi doi CRLF (R-M2).
export async function sendMessageRequest(
  side: MessageSide,
  applicationId: string,
  input: { body: string; file: File | null },
): Promise<Message> {
  const form = new FormData()
  if (input.body.length > 0) {
    form.append('body', input.body)
  }
  if (input.file) {
    form.append('file', input.file)
  }
  const response = await http.post<Message>(threadPath(side, applicationId), form)
  return response.data
}

// M3
export async function markConversationReadRequest(side: MessageSide, applicationId: string): Promise<void> {
  await http.patch(`${threadPath(side, applicationId)}/read`)
}

// M5 - hop thu. Chi doc, KHONG danh dau da doc (R-I4). size de mac dinh backend (20).
export async function listConversationsRequest(
  side: MessageSide,
  page: number,
): Promise<ConversationPage<ConversationHr | ConversationCandidate>> {
  const path = side === 'hr' ? '/hr/messages/conversations' : '/candidates/messages/conversations'
  const response = await http.get<ConversationPage<ConversationHr | ConversationCandidate>>(path, {
    params: { page },
  })
  return response.data
}

// M4 - responseType 'blob' bat buoc: endpoint can header Authorization (Bearer token, lib/http.ts), the
// <a href> thuong khong gan duoc header nay. Ten tep lay tu attachment.fileName (da ep duoi o backend, R-F6) nen
// khong can doc Content-Disposition.
export async function downloadMessageAttachmentRequest(
  side: MessageSide,
  applicationId: string,
  messageId: string,
): Promise<Blob> {
  const response = await http.get(`${threadPath(side, applicationId)}/${messageId}/attachment`, {
    responseType: 'blob',
  })
  return response.data as Blob
}

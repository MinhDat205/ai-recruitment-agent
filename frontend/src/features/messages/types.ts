import type { ApplicationStatus } from '../applications/types'

// FR-C06 - khop DTO backend (REQUIREMENT muc 4.4). Khong co senderId, readAt, attachmentKey hay URL tep.

// Phia dang xem - chon tien to API /hr/... hay /candidates/... (R-C2: mot bo component dung chung hai phia).
export type MessageSide = 'hr' | 'candidate'

export type MessageSenderRole = 'HR' | 'CANDIDATE'

export type MessageAttachmentType = 'PDF' | 'DOCX' | 'PNG' | 'JPEG' | 'WEBP'

export interface MessageAttachment {
  fileName: string
  fileType: MessageAttachmentType
  fileSize: number
}

export interface Message {
  id: string
  senderRole: MessageSenderRole
  mine: boolean
  body: string | null
  attachment: MessageAttachment | null
  createdAt: string
}

// M1
export interface MessageThread {
  canSend: boolean
  olderMessagesHidden: boolean
  unreadCount: number
  messages: Message[]
}

// M5 - khop PageResponse backend (common/dto/PageResponse).
export interface ConversationPage<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// M5 phia HR
export interface ConversationHr {
  applicationId: string
  jobId: string
  jobTitle: string
  candidateName: string
  applicationStatus: ApplicationStatus
  lastMessageAt: string
  lastMessageExcerpt: string | null
  lastMessageMine: boolean
  lastMessageHasAttachment: boolean
  unreadCount: number
}

// M5 phia ung vien - companyName thay cho ten ben kia, khong co ho ten/email HR (R-I2).
export interface ConversationCandidate {
  applicationId: string
  jobId: string
  jobTitle: string
  companyName: string
  applicationStatus: ApplicationStatus
  lastMessageAt: string
  lastMessageExcerpt: string | null
  lastMessageMine: boolean
  lastMessageHasAttachment: boolean
  unreadCount: number
}

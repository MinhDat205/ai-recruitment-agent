export type NotificationType =
  | 'APPLICATION_STATUS_CHANGED'
  | 'APPLICATION_SUBMITTED'
  | 'APPLICATION_WITHDRAWN'
  | 'SCORING_FINISHED'
  | 'NEW_MESSAGE'
  // FR-H12 R-N1 - HR gui lai giay moi; hien mot dong nhu cac loai cu (Q5).
  | 'INTERVIEW_INVITATION_RESENT'
  // FR-U10 R-N2 - ung vien da chon khung gio (chi chu cong ty nhan); mot dong nhu cac loai cu.
  | 'INTERVIEW_SLOT_CONFIRMED'

export interface NotificationItem {
  id: string
  type: NotificationType
  title: string
  body: string | null
  link: string | null
  entityType: string | null
  entityId: string | null
  isRead: boolean
  readAt: string | null
  createdAt: string
}

interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// Khop NotificationPageResponse backend (page + unreadCount boc chung) - KHONG co endpoint
// /unread-count rieng, so chua doc luon doc tu day.
export interface NotificationPageResponse {
  page: Page<NotificationItem>
  unreadCount: number
}

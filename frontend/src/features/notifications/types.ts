export type NotificationType =
  | 'APPLICATION_STATUS_CHANGED'
  | 'APPLICATION_SUBMITTED'
  | 'APPLICATION_WITHDRAWN'
  | 'SCORING_FINISHED'

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

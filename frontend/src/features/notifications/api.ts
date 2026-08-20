import { http } from '../../lib/http'
import type { NotificationItem, NotificationPageResponse } from './types'

export async function listNotificationsRequest(page: number, size: number): Promise<NotificationPageResponse> {
  const response = await http.get<NotificationPageResponse>('/notifications', { params: { page, size } })
  return response.data
}

export async function markNotificationReadRequest(id: string): Promise<NotificationItem> {
  const response = await http.patch<NotificationItem>(`/notifications/${id}/read`)
  return response.data
}

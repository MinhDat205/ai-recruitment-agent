import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { useMarkNotificationReadMutation } from './queries'
import type { NotificationItem } from './types'

function notificationsPagePath(role: string | undefined): string {
  return role === 'HR' ? '/hr/notifications' : '/candidate/notifications'
}

function formatCreatedAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function NotificationDropdown({ items }: { items: NotificationItem[] }) {
  const { user } = useAuth()
  const markReadMutation = useMarkNotificationReadMutation()
  const viewAllPath = notificationsPagePath(user?.role)

  return (
    <div className="flex flex-col">
      <div className="border-b border-m3-outline-variant px-4 py-3">
        <p className="text-sm font-medium text-m3-on-surface">Thông báo</p>
      </div>

      <div className="max-h-96 overflow-y-auto">
        {items.length === 0 ? (
          <p className="px-4 py-6 text-center text-sm text-m3-on-surface-variant">Chưa có thông báo nào.</p>
        ) : (
          items.map((item) => (
            <Link
              key={item.id}
              to={item.link ?? viewAllPath}
              onClick={() => {
                if (!item.isRead) {
                  markReadMutation.mutate(item.id)
                }
              }}
              className={`flex flex-col gap-1 border-b border-m3-outline-variant px-4 py-3 text-sm last:border-b-0 hover:bg-m3-primary-container/40 ${
                item.isRead ? '' : 'bg-m3-primary-container/40'
              }`}
            >
              <span className="font-medium text-m3-on-surface">{item.title}</span>
              {item.body && <span className="text-m3-on-surface-variant">{item.body}</span>}
              <span className="text-xs text-m3-on-surface-variant">{formatCreatedAt(item.createdAt)}</span>
            </Link>
          ))
        )}
      </div>

      <Link
        to={viewAllPath}
        className="border-t border-m3-outline-variant px-4 py-3 text-center text-sm font-medium text-m3-on-primary-container hover:bg-m3-surface-container"
      >
        Xem tất cả
      </Link>
    </div>
  )
}

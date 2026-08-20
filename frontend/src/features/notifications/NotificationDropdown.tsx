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
      <div className="border-b border-line px-4 py-3">
        <p className="text-sm font-medium text-ink">Thông báo</p>
      </div>

      <div className="max-h-96 overflow-y-auto">
        {items.length === 0 ? (
          <p className="px-4 py-6 text-center text-sm text-ink-muted">Chưa có thông báo nào.</p>
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
              className={`flex flex-col gap-1 border-b border-line px-4 py-3 text-sm last:border-b-0 hover:bg-canvas ${
                item.isRead ? '' : 'bg-brand-light/40'
              }`}
            >
              <span className="font-medium text-ink">{item.title}</span>
              {item.body && <span className="text-ink-muted">{item.body}</span>}
              <span className="text-xs text-ink-muted">{formatCreatedAt(item.createdAt)}</span>
            </Link>
          ))
        )}
      </div>

      <Link
        to={viewAllPath}
        className="border-t border-line px-4 py-3 text-center text-sm font-medium text-brand hover:bg-canvas"
      >
        Xem tất cả
      </Link>
    </div>
  )
}

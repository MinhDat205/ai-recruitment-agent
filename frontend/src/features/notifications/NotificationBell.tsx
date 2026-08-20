import { Bell } from 'lucide-react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { NotificationBadge } from './NotificationBadge'
import { NotificationDropdown } from './NotificationDropdown'
import { useNotificationBellQuery } from './queries'

export function NotificationBell() {
  const { data, resumePolling } = useNotificationBellQuery()
  const unreadCount = data?.unreadCount ?? 0

  return (
    // Mo chuong = "tai vu" stall-guard (resumePolling) - nguoi dung vua mo ra la dang chu dong
    // quan tam, hop ly de tinh lai nguong thoi gian toi da tu day.
    <Popover onOpenChange={(open) => open && resumePolling()}>
      <PopoverTrigger asChild>
        <button
          type="button"
          aria-label="Thông báo"
          className="relative flex h-10 w-10 items-center justify-center rounded-md text-ink-muted hover:bg-canvas hover:text-ink"
        >
          <Bell size={20} />
          <NotificationBadge count={unreadCount} />
        </button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80 p-0">
        <NotificationDropdown items={data?.page.items ?? []} />
      </PopoverContent>
    </Popover>
  )
}

import { PublicLayout } from '../components/layout/PublicLayout'
import { NotificationList } from '../features/notifications/NotificationList'

export function CandidateNotificationsPage() {
  return (
    <PublicLayout>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-4 py-8 md:px-6">
        <h1 className="text-xl font-semibold text-ink">Thông báo</h1>
        <NotificationList />
      </div>
    </PublicLayout>
  )
}

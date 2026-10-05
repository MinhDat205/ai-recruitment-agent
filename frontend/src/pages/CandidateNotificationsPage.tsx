import { CandidateLayout } from '../components/layout/CandidateLayout'
import { NotificationList } from '../features/notifications/NotificationList'

export function CandidateNotificationsPage() {
  return (
    <CandidateLayout>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-4 py-8 md:px-6">
        <h1 className="text-xl font-semibold text-m3-on-surface">Thông báo</h1>
        <NotificationList />
      </div>
    </CandidateLayout>
  )
}

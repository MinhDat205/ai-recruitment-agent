import { HrLayout } from '../components/layout/HrLayout'
import { NotificationList } from '../features/notifications/NotificationList'

export function HrNotificationsPage() {
  return (
    <HrLayout title="Thông báo">
      <NotificationList />
    </HrLayout>
  )
}

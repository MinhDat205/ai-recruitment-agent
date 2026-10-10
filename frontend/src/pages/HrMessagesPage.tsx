import { Card, CardHeader } from '@/components/ui/card'
import { HrLayout } from '../components/layout/HrLayout'
import { ConversationList } from '../features/messages/ConversationList'

// FR-C06 - hop thu "Tin nhan" phia HR (route /hr/messages trong nhom ProtectedRoute(HR) + RequireCompany).
// UI.md muc 4d: HrLayout title "Tin nhan" + MOT Card trang max-w-5xl; mot h1 duy nhat tren trang.
export function HrMessagesPage() {
  return (
    <HrLayout title="Tin nhắn">
      <Card className="mx-auto max-w-5xl gap-0 pb-0">
        <CardHeader className="pb-3">
          <h1 className="text-xl font-semibold text-m3-on-surface">Tin nhắn</h1>
        </CardHeader>
        <ConversationList side="hr" />
      </Card>
    </HrLayout>
  )
}

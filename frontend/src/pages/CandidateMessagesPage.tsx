import { Card } from '@/components/ui/card'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { ConversationList } from '../features/messages/ConversationList'

// FR-C06 - hop thu "Tin nhan" phia ung vien (route /candidate/messages trong nhom ProtectedRoute(CANDIDATE) +
// RequireCandidateProfileOnboarding). UI.md muc 4d: khung trang nhu CandidateNotificationsPage, h1 "Tin nhan", roi
// MOT Card trang. Dong hop thu hien TEN CONG TY, khong hien ho ten/email HR (R-I2).
export function CandidateMessagesPage() {
  return (
    <CandidateLayout>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-4 py-8 md:px-6">
        <h1 className="text-xl font-semibold text-m3-on-surface">Tin nhắn</h1>
        <Card className="gap-0 py-0">
          <ConversationList side="candidate" />
        </Card>
      </div>
    </CandidateLayout>
  )
}

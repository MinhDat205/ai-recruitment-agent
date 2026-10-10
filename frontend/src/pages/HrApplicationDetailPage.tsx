import { useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { AlertCircle } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { HrLayout } from '../components/layout/HrLayout'
import { ApplicationDetailHeader } from '../features/applicationDetail/ApplicationDetailHeader'
import { CvAndScoreTab } from '../features/applicationDetail/CvAndScoreTab'
import { ExplanationTab } from '../features/applicationDetail/ExplanationTab'
import { HistoryTab } from '../features/applicationDetail/HistoryTab'
import { applicationDetailKey, useApplicationHrDetailQuery } from '../features/applicationDetail/queries'
import { MessagesTab } from '../features/messages/MessagesTab'

// FR-H09 R-P1 - 3 tab cua FR-H09; FR-C06 R-P3 them DUNG mot tab "Trao doi" (khoa messages) vao CUOI, khong doi
// tab mac dinh hay tab cu. ?tab= doc nhu trang sua Job (HrJobEditPage), gia tri la -> cv (R-E4).
const VALID_TABS = ['cv', 'explanation', 'history', 'messages'] as const
type DetailTab = (typeof VALID_TABS)[number]

function parseTab(value: string | null): DetailTab {
  return (VALID_TABS as readonly string[]).includes(value ?? '') ? (value as DetailTab) : 'cv'
}

// R-T13 - 400 (id khong phai UUID), 403 (don cong ty khac), 404 (khong ton tai) hien CUNG mot thong
// diep, khong phan biet.
function isNotFoundError(error: unknown): boolean {
  if (!isAxiosError(error)) {
    return false
  }
  const status = error.response?.status
  return status === 400 || status === 403 || status === 404
}

function HeaderSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-hidden="true">
      <div className="h-6 w-1/3 animate-pulse rounded bg-m3-surface-container" />
      <div className="h-5 w-24 animate-pulse rounded bg-m3-surface-container" />
      <div className="flex gap-2">
        <div className="h-8 w-24 animate-pulse rounded bg-m3-surface-container" />
        <div className="h-8 w-24 animate-pulse rounded bg-m3-surface-container" />
        <div className="h-8 w-24 animate-pulse rounded bg-m3-surface-container" />
      </div>
    </div>
  )
}

// Trang ho so don ung tuyen (FR-H09, route /hr/applications/:id trong nhom ProtectedRoute(HR) +
// RequireCompany). Tai dau trang (E1) truoc; moi tab chi goi API cua minh khi duoc mo (Radix
// TabsContent khong render tab dang an). E1 loi 400/403/404 -> khong render tab nao, khong goi E2-E5.
// Trang KHONG goi AI va KHONG tu tao luot cham (muc 5 REQUIREMENT).
export function HrApplicationDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams, setSearchParams] = useSearchParams()

  if (!id) {
    return null
  }

  const tab = parseTab(searchParams.get('tab'))

  // Doi tab cap nhat ?tab= bang replace - khong day mot muc lich su moi moi lan bam tab (UI.md muc 2).
  function handleTabChange(value: string) {
    setSearchParams(
      (previous) => {
        const next = new URLSearchParams(previous)
        next.set('tab', parseTab(value))
        return next
      },
      { replace: true },
    )
  }

  return (
    <HrLayout title="Hồ sơ ứng tuyển">
      <Card className="mx-auto max-w-5xl">
        <ApplicationDetailContent applicationId={id} tab={tab} onTabChange={handleTabChange} />
      </Card>
    </HrLayout>
  )
}

function ApplicationDetailContent({
  applicationId,
  tab,
  onTabChange,
}: {
  applicationId: string
  tab: DetailTab
  onTabChange: (value: string) => void
}) {
  const detailQuery = useApplicationHrDetailQuery(applicationId)
  const queryClient = useQueryClient()

  if (detailQuery.isLoading) {
    return (
      <CardContent>
        <HeaderSkeleton />
      </CardContent>
    )
  }

  // Chi coi la loi khi CHUA co du lieu - lan tai lai ngam (sau thao tac) loi thi giu du lieu dang hien.
  if (!detailQuery.data) {
    if (isNotFoundError(detailQuery.error)) {
      return (
        <CardContent className="flex flex-col items-start gap-2">
          <p className="text-sm text-m3-on-surface">Không tìm thấy đơn ứng tuyển.</p>
          <Link to="/hr/candidates" className="text-sm text-m3-primary hover:underline">
            Về danh sách ứng viên
          </Link>
        </CardContent>
      )
    }
    return (
      <CardContent className="flex flex-col items-start gap-2">
        <p role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          Không tải được hồ sơ đơn ứng tuyển.
        </p>
        <Button type="button" variant="outline" size="sm" onClick={() => detailQuery.refetch()}>
          Thử lại
        </Button>
      </CardContent>
    )
  }

  const detail = detailQuery.data

  return (
    <Tabs value={tab} onValueChange={onTabChange}>
      <CardHeader className="gap-4">
        <ApplicationDetailHeader detail={detail} />
        {/* Cung cau hinh TabsList voi trang sua Job; max-w-full overflow-x-auto + justify-start de chi
            TabsList cuon ngang o 375px, khong cuon ca trang (UI.md muc 8). overflow-y-hidden (FR-U08 soat tay):
            overflow-x khac visible lam overflow-y bi tinh thanh auto, lo thanh cuon doc canh tab cuoi. */}
        <TabsList className="mt-2 w-fit max-w-full justify-start overflow-x-auto overflow-y-hidden">
          <TabsTrigger value="cv">CV & điểm</TabsTrigger>
          <TabsTrigger value="explanation">Giải thích</TabsTrigger>
          <TabsTrigger value="history">Lịch sử</TabsTrigger>
          <TabsTrigger value="messages">Trao đổi</TabsTrigger>
        </TabsList>
      </CardHeader>

      <TabsContent value="cv" className="p-4 sm:p-6">
        <CvAndScoreTab detail={detail} />
      </TabsContent>
      <TabsContent value="explanation" className="p-4 sm:p-6">
        <ExplanationTab applicationId={detail.id} />
      </TabsContent>
      <TabsContent value="history" className="p-4 sm:p-6">
        <HistoryTab applicationId={detail.id} />
      </TabsContent>
      <TabsContent value="messages" className="p-4 sm:p-6">
        {/* FR-C06 - 409 CONVERSATION_READ_ONLY: lam moi E1 de badge dau trang cap nhat (UI.md muc 6). */}
        <MessagesTab
          side="hr"
          applicationId={detail.id}
          onReadOnlyConflict={() => queryClient.invalidateQueries({ queryKey: applicationDetailKey(detail.id) })}
        />
      </TabsContent>
    </Tabs>
  )
}

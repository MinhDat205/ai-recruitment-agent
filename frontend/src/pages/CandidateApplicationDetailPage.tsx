import { isAxiosError } from 'axios'
import { AlertCircle } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { ApplicationHistoryTimeline } from '../features/applications/ApplicationHistoryTimeline'
import { useApplicationHistoryQuery } from '../features/applications/queries'
import { ApplicationInfoTab } from '../features/candidateApplicationDetail/ApplicationInfoTab'
import { CandidateApplicationHeader } from '../features/candidateApplicationDetail/CandidateApplicationHeader'
import { useCandidateApplicationDetailQuery } from '../features/candidateApplicationDetail/queries'

// FR-U08 R-P1 - DUNG 2 tab, khong dung tab/khoi/nut cho FR sau (R-P2). ?tab= doc nhu trang ho so don phia HR,
// gia tri la -> info (R-E3).
const VALID_TABS = ['info', 'history'] as const
type DetailTab = (typeof VALID_TABS)[number]

function parseTab(value: string | null): DetailTab {
  return (VALID_TABS as readonly string[]).includes(value ?? '') ? (value as DetailTab) : 'info'
}

// R-T1 - 400 (id khong phai UUID), 403, 404 (don cua nguoi khac hoac khong ton tai) hien CUNG mot thong diep.
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
      <div className="h-4 w-40 animate-pulse rounded bg-m3-surface-container" />
      <div className="h-6 w-1/2 animate-pulse rounded bg-m3-surface-container" />
      <div className="h-5 w-28 animate-pulse rounded bg-m3-surface-container" />
      <div className="flex gap-2">
        <div className="h-8 w-28 animate-pulse rounded bg-m3-surface-container" />
        <div className="h-8 w-24 animate-pulse rounded bg-m3-surface-container" />
      </div>
    </div>
  )
}

// Tab "Lịch sử" (R-T14, R-D9) - endpoint lich su co san cua ung vien, giao dien ApplicationHistoryTimeline
// nguyen trang. Chi mount khi tab duoc mo nen chi goi API luc do.
function HistoryTab({ applicationId }: { applicationId: string }) {
  const { data: history, isLoading, isError } = useApplicationHistoryQuery(applicationId)
  return <ApplicationHistoryTimeline history={history} isLoading={isLoading} isError={isError} />
}

// Trang chi tiet don ung tuyen phia ung vien (FR-U08, route /candidate/applications/:id trong nhom
// ProtectedRoute(CANDIDATE)). Tai E1 truoc; moi tab chi goi API cua minh khi duoc mo (Radix TabsContent
// khong render tab dang an). E1 loi 400/403/404 -> khong render tab, khong goi API nao khac. Khong goi AI.
export function CandidateApplicationDetailPage() {
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
    <CandidateLayout>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-4 py-8 md:px-6">
        <Card className="mx-auto w-full max-w-5xl">
          <CandidateApplicationDetailContent applicationId={id} tab={tab} onTabChange={handleTabChange} />
        </Card>
      </div>
    </CandidateLayout>
  )
}

function CandidateApplicationDetailContent({
  applicationId,
  tab,
  onTabChange,
}: {
  applicationId: string
  tab: DetailTab
  onTabChange: (value: string) => void
}) {
  const detailQuery = useCandidateApplicationDetailQuery(applicationId)

  if (detailQuery.isLoading) {
    return (
      <CardContent>
        <HeaderSkeleton />
      </CardContent>
    )
  }

  // Chi coi la loi khi CHUA co du lieu - lan tai lai ngam (sau khi rut don) loi thi giu du lieu dang hien.
  if (!detailQuery.data) {
    if (isNotFoundError(detailQuery.error)) {
      return (
        <CardContent className="flex flex-col items-start gap-2">
          <p className="text-sm text-m3-on-surface">Không tìm thấy đơn ứng tuyển.</p>
          <Link to="/candidate/applications" className="text-sm font-medium text-m3-primary hover:underline">
            Về danh sách đơn ứng tuyển
          </Link>
        </CardContent>
      )
    }
    return (
      <CardContent className="flex flex-col items-start gap-2">
        <p role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          Không tải được đơn ứng tuyển.
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
        <CandidateApplicationHeader detail={detail} />
        {/* Cung cau hinh TabsList voi trang ho so don phia HR; max-w-full overflow-x-auto + justify-start de
            chi TabsList cuon ngang o 375px, khong cuon ca trang (UI.md muc 8). */}
        <TabsList className="mt-2 w-fit max-w-full justify-start overflow-x-auto">
          <TabsTrigger value="info">Thông tin đơn</TabsTrigger>
          <TabsTrigger value="history">Lịch sử</TabsTrigger>
        </TabsList>
      </CardHeader>

      <TabsContent value="info" className="p-4 sm:p-6">
        <ApplicationInfoTab detail={detail} />
      </TabsContent>
      <TabsContent value="history" className="p-4 sm:p-6">
        <HistoryTab applicationId={detail.id} />
      </TabsContent>
    </Tabs>
  )
}

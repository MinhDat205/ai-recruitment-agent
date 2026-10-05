import { AlertCircle } from 'lucide-react'
import { HrLayout } from '../components/layout/HrLayout'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'
import { useAuth } from '../features/auth/useAuth'
import { ConversionFunnelCard } from '../features/dashboard/ConversionFunnelCard'
import { JobPerformanceTable } from '../features/dashboard/JobPerformanceTable'
import { StatusBreakdownChart } from '../features/dashboard/StatusBreakdownChart'
import { useDashboardStatsQuery } from '../features/dashboard/queries'

export function HrHomePage() {
  const { user } = useAuth()
  const { data, isLoading, isError } = useDashboardStatsQuery()

  return (
    <HrLayout title="Dashboard">
      <div className="flex flex-col gap-6">
        <h1 className="text-2xl font-semibold text-m3-on-surface">Xin chào {user?.fullName}</h1>

        {isLoading && <p className="text-sm text-m3-on-surface">Đang tải...</p>}

        {/* Nen trang xam: chu do chi dat 4.16:1 nen chu dung on-surface, tin hieu loi giu bang icon. */}
        {isError && (
          <div role="alert" className="flex items-start gap-2 text-sm text-m3-on-surface">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
            <p>Không tải được dữ liệu thống kê, vui lòng thử lại.</p>
          </div>
        )}

        {!isLoading && !isError && data && (
          <>
            <Card>
              <CardHeader>
                <CardTitle>Tổng số hồ sơ ứng tuyển: {data.totalApplications}</CardTitle>
              </CardHeader>
              <CardContent>
                <StatusBreakdownChart statusBreakdown={data.statusBreakdown} />
              </CardContent>
            </Card>

            <ConversionFunnelCard funnel={data.funnel} />

            <Card>
              <CardHeader>
                <CardTitle>Hiệu suất từng chiến dịch tuyển dụng</CardTitle>
              </CardHeader>
              <CardContent>
                <JobPerformanceTable items={data.jobPerformance} />
              </CardContent>
            </Card>
          </>
        )}
      </div>
    </HrLayout>
  )
}

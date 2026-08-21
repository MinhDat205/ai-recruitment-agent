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
        <h1 className="text-2xl font-semibold text-ink">Xin chào {user?.fullName}</h1>

        {isLoading && <p className="text-sm text-ink-muted">Đang tải...</p>}

        {isError && (
          <p className="text-sm text-danger">Không tải được dữ liệu thống kê, vui lòng thử lại.</p>
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

import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/card'
import type { ConversionFunnel } from './types'

function toPercent(part: number, total: number): string {
  if (total === 0) {
    return '0%'
  }
  return `${Math.round((part / total) * 100)}%`
}

// Ty le % tinh o frontend tu ba so tho (backend khong tu lam tron - xem DashboardStatsResponse).
// Mot mau brand duy nhat cho ca ba buoc - khong dung do/xanh/vang de goi y "cang ve sau cang tot/
// xau" (cung nguyen tac voi StatusBreakdownChart).
export function ConversionFunnelCard({ funnel }: { funnel: ConversionFunnel }) {
  const steps = [
    { label: 'Đã nộp đơn', count: funnel.appliedTotal, percent: toPercent(funnel.appliedTotal, funnel.appliedTotal) },
    {
      label: 'Đã từng mời phỏng vấn',
      count: funnel.everInvited,
      percent: toPercent(funnel.everInvited, funnel.appliedTotal),
    },
    {
      label: 'Đã từng trúng tuyển',
      count: funnel.everHired,
      percent: toPercent(funnel.everHired, funnel.appliedTotal),
    },
  ]

  return (
    <Card>
      <CardHeader>
        <CardTitle>Tỷ lệ chuyển đổi giữa các vòng</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-3 gap-4">
          {steps.map((step) => (
            <div key={step.label} className="rounded-(--radius-card) border border-line bg-canvas p-4 text-center">
              <p className="text-2xl font-semibold text-brand">{step.count}</p>
              <p className="mt-1 text-xs text-ink-muted">{step.label}</p>
              <p className="mt-1 text-sm font-medium text-ink">{step.percent}</p>
            </div>
          ))}
        </div>
        <p className="mt-3 text-xs text-ink-muted">
          Tính theo lịch sử chuyển trạng thái: đơn đã từng được mời phỏng vấn hoặc trúng tuyển vẫn
          được tính dù sau đó ứng viên đã rút đơn.
        </p>
      </CardContent>
    </Card>
  )
}

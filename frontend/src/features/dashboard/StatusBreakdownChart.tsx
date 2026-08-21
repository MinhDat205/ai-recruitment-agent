import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { APPLICATION_STATUS_LABELS } from '../applications/applicationLabels'
import type { ApplicationStatus } from '../applications/types'

const STATUS_ORDER: ApplicationStatus[] = ['PENDING', 'INTERVIEW_INVITED', 'HIRED', 'REJECTED', 'WITHDRAWN']

// Mot mau brand duy nhat cho ca 5 cot - KHONG dung --color-status-*-text (HIRED xanh la,
// REJECTED do): badge trang thai don le khac voi bieu do dat 5 cot canh nhau de so sanh,
// cot do thap canh cot xanh cao doc ra thanh phan quyet tot-xau (PHASES.md D3 cam).
export function StatusBreakdownChart({ statusBreakdown }: { statusBreakdown: Record<ApplicationStatus, number> }) {
  const data = STATUS_ORDER.map((status) => ({
    label: APPLICATION_STATUS_LABELS[status],
    count: statusBreakdown[status] ?? 0,
  }))

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-line)" vertical={false} />
          <XAxis
            dataKey="label"
            tick={{ fill: 'var(--color-ink-muted)', fontSize: 12 }}
            axisLine={{ stroke: 'var(--color-line)' }}
            tickLine={false}
          />
          <YAxis
            allowDecimals={false}
            tick={{ fill: 'var(--color-ink-muted)', fontSize: 12 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip
            cursor={{ fill: 'var(--color-canvas)' }}
            contentStyle={{ borderRadius: 8, borderColor: 'var(--color-line)', fontSize: 12 }}
          />
          <Bar dataKey="count" name="Số đơn" fill="var(--color-brand)" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

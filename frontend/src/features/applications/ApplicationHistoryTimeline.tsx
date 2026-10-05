import { ArrowRight } from 'lucide-react'
import { ApplicationStatusBadge } from './ApplicationStatusBadge'
import { useApplicationHistoryQuery } from './queries'

function formatChangedAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function ApplicationHistoryTimeline({ applicationId }: { applicationId: string }) {
  const { data: history, isLoading, isError } = useApplicationHistoryQuery(applicationId)

  if (isLoading) {
    return <p className="text-sm text-m3-on-surface-variant">Đang tải lịch sử...</p>
  }

  if (isError) {
    return <p className="text-sm text-m3-error">Không tải được lịch sử, vui lòng thử lại.</p>
  }

  if (!history || history.length === 0) {
    return <p className="text-sm text-m3-on-surface-variant">Chưa có lịch sử chuyển trạng thái.</p>
  }

  return (
    <ol className="flex flex-col gap-4">
      {history.map((entry) => (
        <li key={entry.id} className="flex flex-col gap-1 border-l-2 border-m3-outline-variant pl-3">
          <div className="flex items-center gap-2">
            {entry.fromStatus ? (
              <>
                <ApplicationStatusBadge status={entry.fromStatus} />
                <ArrowRight className="h-3.5 w-3.5 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
              </>
            ) : (
              <span className="text-xs text-m3-on-surface-variant">Nộp đơn</span>
            )}
            <ApplicationStatusBadge status={entry.toStatus} />
          </div>
          <span className="text-xs text-m3-on-surface-variant">{formatChangedAt(entry.changedAt)}</span>
          {entry.note && <p className="text-sm text-m3-on-surface">{entry.note}</p>}
        </li>
      ))}
    </ol>
  )
}

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
    return <p className="text-sm text-ink-muted">Đang tải lịch sử...</p>
  }

  if (isError) {
    return <p className="text-sm text-danger">Không tải được lịch sử, vui lòng thử lại.</p>
  }

  if (!history || history.length === 0) {
    return <p className="text-sm text-ink-muted">Chưa có lịch sử chuyển trạng thái.</p>
  }

  return (
    <ol className="flex flex-col gap-4">
      {history.map((entry) => (
        <li key={entry.id} className="flex flex-col gap-1 border-l-2 border-line pl-3">
          <div className="flex items-center gap-2">
            {entry.fromStatus ? (
              <>
                <ApplicationStatusBadge status={entry.fromStatus} />
                <ArrowRight className="h-3.5 w-3.5 shrink-0 text-ink-muted" aria-hidden="true" />
              </>
            ) : (
              <span className="text-xs text-ink-muted">Nộp đơn</span>
            )}
            <ApplicationStatusBadge status={entry.toStatus} />
          </div>
          <span className="text-xs text-ink-muted">{formatChangedAt(entry.changedAt)}</span>
          {entry.note && <p className="text-sm text-ink">{entry.note}</p>}
        </li>
      ))}
    </ol>
  )
}

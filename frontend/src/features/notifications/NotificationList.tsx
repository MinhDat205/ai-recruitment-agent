import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { useMarkNotificationReadMutation, useNotificationsListQuery } from './queries'

function formatCreatedAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// Dung chung cho trang "xem tat ca" cua ca HR lan candidate. State phan trang qua useSearchParams
// (dong bo URL ?page=), giong PublicJobListPage/JobList - phan trang viet lai cuc bo o day thay vi
// import features/jobs/Pagination.tsx, dung quy uoc khong dung chung component cheo feature.
export function NotificationList() {
  const [searchParams, setSearchParams] = useSearchParams()
  const pageParam = Number(searchParams.get('page') ?? '0')
  const page = Number.isNaN(pageParam) ? 0 : pageParam

  const { data, isLoading, isError, refetch } = useNotificationsListQuery(page)
  const markReadMutation = useMarkNotificationReadMutation()

  function handlePageChange(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    setSearchParams(next)
  }

  if (isLoading) {
    return <p className="text-sm text-ink-muted">Đang tải...</p>
  }

  if (isError) {
    return (
      <div className="flex flex-col items-start gap-2">
        <p className="text-sm text-danger">Không tải được danh sách thông báo.</p>
        <button type="button" onClick={() => refetch()} className="text-sm font-medium text-brand hover:underline">
          Thử lại
        </button>
      </div>
    )
  }

  const items = data?.page.items ?? []

  if (items.length === 0) {
    return <p className="text-sm text-ink-muted">Bạn chưa có thông báo nào.</p>
  }

  return (
    <div className="flex flex-col gap-2">
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          onClick={() => {
            if (!item.isRead) {
              markReadMutation.mutate(item.id)
            }
          }}
          className={`flex flex-col gap-1 rounded-(--radius-card) border border-line p-4 text-left ${
            item.isRead ? 'bg-surface' : 'bg-brand-light/40'
          }`}
        >
          <div className="flex items-center justify-between gap-4">
            <p className="text-sm font-medium text-ink">{item.title}</p>
            <span className="shrink-0 text-xs text-ink-muted">{formatCreatedAt(item.createdAt)}</span>
          </div>
          {item.body && <p className="text-sm text-ink-muted">{item.body}</p>}
        </button>
      ))}

      {data && data.page.totalPages > 1 && (
        <div className="flex items-center justify-center gap-4 py-4">
          <button
            type="button"
            aria-label="Trang trước"
            disabled={page <= 0}
            onClick={() => handlePageChange(page - 1)}
            className="flex h-9 w-9 items-center justify-center rounded-(--radius-badge) border border-line text-ink disabled:cursor-not-allowed disabled:opacity-40"
          >
            <ChevronLeft size={18} />
          </button>
          <span className="text-sm text-ink-muted">
            Trang {page + 1} / {data.page.totalPages}
          </span>
          <button
            type="button"
            aria-label="Trang sau"
            disabled={page >= data.page.totalPages - 1}
            onClick={() => handlePageChange(page + 1)}
            className="flex h-9 w-9 items-center justify-center rounded-(--radius-badge) border border-line text-ink disabled:cursor-not-allowed disabled:opacity-40"
          >
            <ChevronRight size={18} />
          </button>
        </div>
      )}
    </div>
  )
}

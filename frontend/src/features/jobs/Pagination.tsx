import { ChevronLeft, ChevronRight } from 'lucide-react'

interface PaginationProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) {
    return null
  }

  return (
    <div className="flex items-center justify-center gap-4 py-6">
      <button
        type="button"
        aria-label="Trang trước"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
        className="flex h-9 w-9 items-center justify-center rounded-(--radius-badge) border border-line text-ink disabled:cursor-not-allowed disabled:opacity-40"
      >
        <ChevronLeft size={18} />
      </button>

      <span className="text-sm text-ink-muted">
        Trang {page + 1} / {totalPages}
      </span>

      <button
        type="button"
        aria-label="Trang sau"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
        className="flex h-9 w-9 items-center justify-center rounded-(--radius-badge) border border-line text-ink disabled:cursor-not-allowed disabled:opacity-40"
      >
        <ChevronRight size={18} />
      </button>
    </div>
  )
}

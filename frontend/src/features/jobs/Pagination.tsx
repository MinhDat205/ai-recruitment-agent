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
        className="flex h-12 w-12 items-center justify-center rounded-(--radius-badge) border border-m3-outline text-m3-on-surface disabled:cursor-not-allowed disabled:opacity-40 sm:h-9 sm:w-9"
      >
        <ChevronLeft size={18} />
      </button>

      <span className="text-sm text-m3-on-surface">
        Trang {page + 1} / {totalPages}
      </span>

      <button
        type="button"
        aria-label="Trang sau"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
        className="flex h-12 w-12 items-center justify-center rounded-(--radius-badge) border border-m3-outline text-m3-on-surface disabled:cursor-not-allowed disabled:opacity-40 sm:h-9 sm:w-9"
      >
        <ChevronRight size={18} />
      </button>
    </div>
  )
}

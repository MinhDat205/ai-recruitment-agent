import { useState } from 'react'
import { HrLayout } from '../components/layout/HrLayout'
import { CandidatesFilterBar } from '../features/candidates/CandidatesFilterBar'
import { CandidatesTable } from '../features/candidates/CandidatesTable'
import { useCandidatesQuery } from '../features/candidates/queries'
import type { CandidateSearchParams } from '../features/candidates/types'
import { Pagination } from '../features/jobs/Pagination'

const PAGE_SIZE = 10

export function HrCandidatesPage() {
  const [filters, setFilters] = useState<CandidateSearchParams>({})
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useCandidatesQuery({ ...filters, page, size: PAGE_SIZE })

  function handleApplyFilters(next: CandidateSearchParams) {
    setFilters(next)
    setPage(0)
  }

  return (
    <HrLayout title="Ứng viên">
      <div className="flex flex-col gap-4">
        <CandidatesFilterBar onApply={handleApplyFilters} />

        {isLoading && <p className="text-sm text-ink-muted">Đang tải...</p>}

        {isError && <p className="text-sm text-danger">Không tải được danh sách ứng viên, vui lòng thử lại.</p>}

        {!isLoading && !isError && data && (
          <>
            <CandidatesTable items={data.items} />
            <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
          </>
        )}
      </div>
    </HrLayout>
  )
}

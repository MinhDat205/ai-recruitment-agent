import { RotateCw } from 'lucide-react'
import { useState } from 'react'
import { Button } from '../components/ui/button'
import { HrLayout } from '../components/layout/HrLayout'
import { CandidatesFilterBar } from '../features/candidates/CandidatesFilterBar'
import { CandidatesTable } from '../features/candidates/CandidatesTable'
import { useCandidatesQuery, useCreateScoringRunMutation } from '../features/candidates/queries'
import type { CandidateSearchParams } from '../features/candidates/types'
import { extractErrorMessage } from '../lib/httpError'
import { Pagination } from '../features/jobs/Pagination'

const PAGE_SIZE = 10

export function HrCandidatesPage() {
  const [filters, setFilters] = useState<CandidateSearchParams>({})
  const [page, setPage] = useState(0)

  const { data, isLoading, isError, isFetching, refetch } = useCandidatesQuery({ ...filters, page, size: PAGE_SIZE })
  const createScoringRunMutation = useCreateScoringRunMutation()

  function handleApplyFilters(next: CandidateSearchParams) {
    setFilters(next)
    setPage(0)
  }

  return (
    <HrLayout title="Ứng viên">
      <div className="flex flex-col gap-4">
        <CandidatesFilterBar
          onApply={handleApplyFilters}
          extraActions={
            <Button type="button" variant="outline" size="sm" disabled={isFetching} onClick={() => refetch()}>
              <RotateCw className="h-3.5 w-3.5" aria-hidden="true" />
              Tải lại
            </Button>
          }
        />

        {isLoading && <p className="text-sm text-m3-on-surface">Đang tải...</p>}

        {isError && <p className="text-sm text-m3-on-surface">Không tải được danh sách ứng viên, vui lòng thử lại.</p>}

        {!isLoading && !isError && data && (
          <>
            {createScoringRunMutation.isError && (
              <p className="text-sm text-m3-on-surface">
                {extractErrorMessage(createScoringRunMutation.error, 'Tạo lượt chấm điểm thất bại, vui lòng thử lại.')}
              </p>
            )}

            <CandidatesTable
              items={data.items}
              onScore={(applicationId) => createScoringRunMutation.mutate(applicationId)}
              scoringApplicationId={createScoringRunMutation.isPending ? createScoringRunMutation.variables : undefined}
            />
            <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
          </>
        )}
      </div>
    </HrLayout>
  )
}

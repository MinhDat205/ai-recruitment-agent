import { RotateCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatDateVi } from '../../lib/date'
import { ExplanationReport } from '../scoring/ExplanationReport'
import { useApplicationExplanationQuery } from './queries'
import { SectionSkeleton, TabLoadError } from './ScoreSection'

// Tab "Giải thích" (R-T8..R-T11). Bao cao FR-H06 cua CUNG lot DONE voi diem o tab "CV & điểm" (E4 tra
// scoringRunId/scoredAt trung E3, R-D6). ExplanationReport giu nguyen trang (co dong "khong phai
// khuyen nghi tuyen dung"). KHONG tu poll khi PENDING (R-T14) - chi co nut "Tải lại" goi lai E4; mo lai
// tab cung tai lai (refetchOnMount 'always' trong queries.ts).
export function ExplanationTab({ applicationId }: { applicationId: string }) {
  const explanationQuery = useApplicationExplanationQuery(applicationId)

  if (explanationQuery.isLoading) {
    return <SectionSkeleton />
  }
  if (!explanationQuery.data) {
    return <TabLoadError onRetry={() => explanationQuery.refetch()} />
  }

  const { scoringRunId, scoredAt, explanationStatus, explanation } = explanationQuery.data

  // R-T8 - ExplanationReport tra null khi ca hai field null, nen trang tu hien cau nay.
  if (scoringRunId === null) {
    return <p className="text-sm text-m3-on-surface">Chưa có báo cáo giải thích vì đơn chưa có lượt chấm hoàn tất.</p>
  }

  const isReloading = explanationQuery.isFetching

  return (
    <div aria-live="polite" className="flex flex-col gap-3">
      <ExplanationReport explanation={explanation} explanationStatus={explanationStatus} />
      {explanationStatus === 'PENDING' && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="w-fit"
          disabled={isReloading}
          onClick={() => explanationQuery.refetch()}
        >
          <RotateCw className="h-3.5 w-3.5" aria-hidden="true" />
          {isReloading ? 'Đang tải lại…' : 'Tải lại'}
        </Button>
      )}
      {explanation !== null && scoredAt && (
        <p className="text-sm text-m3-on-surface-variant">
          Báo cáo của lượt chấm hoàn tất ngày {formatDateVi(scoredAt)}.
        </p>
      )}
    </div>
  )
}

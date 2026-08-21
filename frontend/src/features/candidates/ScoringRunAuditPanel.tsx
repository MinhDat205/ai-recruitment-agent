import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../../components/ui/table'
import { Sheet, SheetBody, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '../../components/ui/sheet'
import { formatScore } from '../../lib/score'
import { ApplicationStatusBadge } from '../applications/ApplicationStatusBadge'
import type { CriterionScoreItem, ScoringRunStatus } from '../scoring/types'
import { useScoringRunAuditQuery } from './queries'
import type { CandidateSearchItem } from './types'

// Nhan trang thai RIENG cho panel audit - KHONG tai dung ScoringRunStatusBadge (features/scoring)
// vi component do doi criteriaScored/criteriaTotal la TIEN DO THAT (vd "Da cham 2/5 tieu chi"),
// con ScoringRunAuditItemResponse chi co criterionScores.length (so tieu chi DA cham xong cua
// LUOT DO), khong co tong so tieu chi cua rubric goc - dua hai so nay vao se hien sai kieu "N/N"
// (luon day du) ke ca voi mot luot FAILED giua chung. Panel audit xem LICH SU tinh, khong phai
// theo doi tien do song nhu ApplicationsTab, nen chi can nhan trang thai don gian, trung tinh.
const STATUS_LABELS: Record<ScoringRunStatus, string> = {
  PENDING: 'Đang chờ xử lý',
  RUNNING: 'Đang chấm điểm',
  DONE: 'Đã hoàn tất',
  FAILED: 'Thất bại',
}

const STATUS_TONE: Record<ScoringRunStatus, string> = {
  PENDING: 'bg-canvas text-ink-muted',
  RUNNING: 'bg-canvas text-ink-muted',
  DONE: 'bg-brand-light text-brand',
  FAILED: 'bg-canvas text-ink-muted',
}

function StatusLabel({ status }: { status: ScoringRunStatus }) {
  return (
    <span
      className={`inline-flex items-center rounded-(--radius-badge) px-2 py-1 text-xs font-medium ${STATUS_TONE[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  )
}

function formatDateTime(iso: string | null): string {
  if (iso === null) return '—'
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

// Bang gon RIENG cho panel audit - KHONG tai dung CriterionScoreBreakdown (features/scoring,
// D4): component do render reasoning + evidence DAY DU cho tung tieu chi (gap/mo), phu hop khi
// xem MOT lot cham trong Sheet cua ApplicationsTab, nhung panel audit xep NHIEU lot canh nhau -
// dung nguyen component do se lam panel dai le the va lap lai dung noi dung da co o Sheet D4.
// Panel audit la LOG KY THUAT (model/version/token/diem), khong phai noi doc lai bao cao danh
// gia - chi can ten tieu chi/diem-thang diem/trong so dang bang, khong mau, khong evidence.
function CriterionScoreAuditTable({ criterionScores }: { criterionScores: CriterionScoreItem[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Tiêu chí</TableHead>
          <TableHead>Điểm</TableHead>
          <TableHead>Trọng số</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {criterionScores.map((item) => (
          <TableRow key={item.criterionNameSnapshot}>
            <TableCell className="text-ink">{item.criterionNameSnapshot}</TableCell>
            <TableCell className="text-ink-muted">
              {item.score}/{item.maxScoreSnapshot}
            </TableCell>
            <TableCell className="text-ink-muted">{item.weightSnapshot}%</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

export function ScoringRunAuditPanel({
  application,
  onOpenChange,
}: {
  application: CandidateSearchItem | null
  onOpenChange: (open: boolean) => void
}) {
  const { data: runs, isLoading, isError } = useScoringRunAuditQuery(application?.id)

  return (
    <Sheet open={application !== null} onOpenChange={onOpenChange}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>Lịch sử đánh giá AI — {application?.candidateName}</SheetTitle>
          <SheetDescription>
            Toàn bộ lượt chấm điểm của đơn này, phục vụ đối chiếu và kiểm tra tuân thủ — mới nhất trước.
          </SheetDescription>
          {application && <ApplicationStatusBadge status={application.status} />}
        </SheetHeader>
        <SheetBody>
          {isLoading && <p className="p-4 text-sm text-ink-muted">Đang tải...</p>}
          {isError && <p className="p-4 text-sm text-danger">Không tải được lịch sử đánh giá, vui lòng thử lại.</p>}
          {!isLoading && !isError && runs && runs.length === 0 && (
            <p className="p-4 text-sm text-ink-muted">Đơn này chưa có lượt chấm điểm nào.</p>
          )}
          {!isLoading && !isError && runs && runs.length > 0 && (
            <div className="flex flex-col gap-4 p-4">
              {runs.map((run) => (
                <div key={run.scoringRunId} className="rounded-(--radius-card) border border-line bg-surface">
                  <div className="flex flex-col gap-2 border-b border-line p-4">
                    <div className="flex items-center justify-between gap-2">
                      <StatusLabel status={run.status} />
                      <span className="text-sm font-medium text-ink">{formatScore(run.totalScore)}</span>
                    </div>
                    <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-ink-muted">
                      <div>
                        <dt className="inline">Tạo lúc: </dt>
                        <dd className="inline text-ink">{formatDateTime(run.createdAt)}</dd>
                      </div>
                      <div>
                        <dt className="inline">Hoàn tất lúc: </dt>
                        <dd className="inline text-ink">{formatDateTime(run.finishedAt)}</dd>
                      </div>
                      <div>
                        <dt className="inline">Model chấm điểm: </dt>
                        <dd className="inline text-ink">{run.model ?? '—'}</dd>
                      </div>
                      <div>
                        <dt className="inline">Phiên bản prompt: </dt>
                        <dd className="inline text-ink">{run.promptVersion ?? '—'}</dd>
                      </div>
                      <div>
                        <dt className="inline">Token sử dụng: </dt>
                        <dd className="inline text-ink">{run.tokenUsage ?? '—'}</dd>
                      </div>
                      <div>
                        <dt className="inline">Báo cáo giải thích: </dt>
                        <dd className="inline text-ink">
                          {run.explanation ? `${run.explanation.model} / ${run.explanation.promptVersion}` : 'Chưa có'}
                        </dd>
                      </div>
                    </dl>
                  </div>
                  {run.criterionScores.length > 0 ? (
                    <CriterionScoreAuditTable criterionScores={run.criterionScores} />
                  ) : (
                    <p className="p-4 text-sm text-ink-muted">Lượt này chưa có điểm tiêu chí nào.</p>
                  )}
                </div>
              ))}
            </div>
          )}
        </SheetBody>
      </SheetContent>
    </Sheet>
  )
}

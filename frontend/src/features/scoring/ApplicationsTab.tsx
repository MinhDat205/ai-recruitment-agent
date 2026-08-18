import { isAxiosError } from 'axios'
import { useState } from 'react'
import { ChevronDown, ChevronRight, RotateCw } from 'lucide-react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ParseStatusBadge } from '../resumes/ParseStatusBadge'
import { CriterionScoreBreakdown } from './CriterionScoreBreakdown'
import { useCreateScoringRunMutation, useHrApplicationsQuery, useScoringRunsQuery } from './queries'
import { ScoringRunStatusBadge } from './ScoringRunStatusBadge'
import type { ApplicationHrListItem, ApplicationSortOption } from './types'

// So cot cua bang (Diem 3, Dot 5) - dung cho colSpan cua hang mo rong. Dem: [chevron, Ung vien,
// Ngay nop, Trang thai CV, Luot cham gan nhat, Hang, Tong diem, Thao tac].
const TABLE_COLUMN_COUNT = 8

// Dau gach ngang trung tinh cho o CHUA CO gia tri (don chua cham / lot FAILED / dang cham dang) -
// KHONG hien "0" (se hieu nham la diem that bang khong) hay chu "Chua cham" (trung lap va co the
// mau thuan voi badge trang thai o cot ben canh, noi da phan biet PENDING/RUNNING/FAILED chi tiet
// hon). O nay chi tra loi "co gia tri hay khong", "vi sao" da co cot Luot cham gan nhat tra loi.
const EMPTY_VALUE_PLACEHOLDER = '—'

function formatAppliedAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatTotalScore(totalScore: number | null): string {
  return totalScore === null ? EMPTY_VALUE_PLACEHOLDER : totalScore.toFixed(2)
}

function formatRank(rank: number | null): string {
  return rank === null ? EMPTY_VALUE_PLACEHOLDER : String(rank)
}

function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

// Dieu kien disable nut "Cham diem ho so" (Dot 5, yeu cau bat buoc): CV chua parse xong, HOAC don
// dang co lot cham chua hoan tat (finishedAt con null VA status la PENDING/RUNNING - dung dieu kien
// tien quyet #5 cua backend, ScoringRunService.requireNoRunInProgress).
//
// TRUONG HOP FAILED (da xac nhan lai theo yeu cau): finishedAt LUON khac null khi status=FAILED
// (backend set ca hai cung luc trong ScoringRunStateService.markFailed) - nen dieu kien
// `latestScoringRunFinishedAt === null` o duoi da tu dong sai (false) cho FAILED, `runInProgress`
// = false, ham nay tra ve undefined (KHONG disable). Day CHINH LA duong phuc hoi duy nhat cua HR
// khi mot lot cham that bai - khong chan nham la loi nghiem trong, da rieng kiem lai va confirm.
function scoringDisabledReason(application: ApplicationHrListItem): string | undefined {
  if (application.resumeParseStatus !== 'DONE') {
    return 'CV của ứng viên chưa được AI trích xuất xong, vui lòng chờ xử lý xong rồi thử lại.'
  }
  const runInProgress =
    application.latestScoringRunFinishedAt === null &&
    (application.latestScoringRunStatus === 'PENDING' || application.latestScoringRunStatus === 'RUNNING')
  if (runInProgress) {
    return 'Đơn này đang có một lượt chấm điểm chưa hoàn tất, vui lòng chờ lượt trước kết thúc.'
  }
  return undefined
}

function ApplicationRow({
  application,
  onScore,
  isScoring,
  isExpanded,
  onToggleExpand,
}: {
  application: ApplicationHrListItem
  onScore: () => void
  isScoring: boolean
  isExpanded: boolean
  onToggleExpand: () => void
}) {
  // Chi can goi lay chi tiet lot cham (criteriaScored/criteriaTotal, errorMessage) khi don NAY dang
  // co hoac da tung co mot lot cham - tranh goi thua cho don chua bao gio duoc bam "Cham diem ho
  // so".
  const hasRun = application.latestScoringRunId !== null
  const {
    data: runs,
    timedOut: rowPollingTimedOut,
    resumePolling: resumeRowPolling,
  } = useScoringRunsQuery(application.id, hasRun)
  const latestRun = runs?.[0]
  const disabledReason = scoringDisabledReason(application)
  const hasCriterionScores = application.criterionScores.length > 0

  return (
    <>
      <TableRow>
        <TableCell className="w-10">
          {hasCriterionScores && (
            <button
              type="button"
              className="flex h-6 w-6 items-center justify-center rounded-(--radius-badge) text-ink-muted hover:bg-canvas"
              onClick={onToggleExpand}
              aria-expanded={isExpanded}
              aria-label={isExpanded ? 'Thu gọn điểm từng tiêu chí' : 'Xem điểm từng tiêu chí'}
            >
              {isExpanded ? (
                <ChevronDown className="h-4 w-4" aria-hidden="true" />
              ) : (
                <ChevronRight className="h-4 w-4" aria-hidden="true" />
              )}
            </button>
          )}
        </TableCell>
        <TableCell>{application.candidateName}</TableCell>
        <TableCell className="text-ink-muted">{formatAppliedAt(application.appliedAt)}</TableCell>
        <TableCell>
          <ParseStatusBadge status={application.resumeParseStatus} />
        </TableCell>
        <TableCell>
          <div className="flex flex-col gap-1">
            {application.latestScoringRunStatus === null ? (
              <span className="text-xs text-ink-muted">Chưa chấm điểm</span>
            ) : (
              <ScoringRunStatusBadge
                status={application.latestScoringRunStatus}
                finishedAt={application.latestScoringRunFinishedAt}
                criteriaScored={latestRun?.criteriaScored ?? 0}
                criteriaTotal={latestRun?.criteriaTotal ?? 0}
              />
            )}
            {application.latestScoringRunStatus === 'FAILED' && latestRun?.errorMessage && (
              <p className="text-xs text-ink-muted">{latestRun.errorMessage}</p>
            )}
            {/* timedOut: lot cham nay dung tu dong cap nhat sau 10 phut khong doi (co the ket vinh
                vien do JVM backend restart giua chung, xem MAX_POLL_DURATION_MS) - HR tu bam de
                kiem tra lai, khong tu dong lap lai vo han. */}
            {rowPollingTimedOut && (
              <button
                type="button"
                className="inline-flex w-fit items-center gap-1 text-xs font-medium text-brand hover:underline"
                onClick={() => resumeRowPolling()}
              >
                <RotateCw className="h-3 w-3" aria-hidden="true" />
                Tải lại
              </button>
            )}
          </div>
        </TableCell>
        {/* Hang/Tong diem: so trung tinh, CUNG mau/kieu chu voi cac cot khac (text-ink) - KHONG to
            mau theo nguong, KHONG in dam du la hang 1. Cam tuyet doi theo srs-guard. */}
        <TableCell className="text-ink">{formatRank(application.rank)}</TableCell>
        <TableCell className="text-ink">{formatTotalScore(application.totalScore)}</TableCell>
        <TableCell className="text-right">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={Boolean(disabledReason) || isScoring}
            title={disabledReason}
            onClick={onScore}
          >
            Chấm điểm hồ sơ
          </Button>
        </TableCell>
      </TableRow>
      {isExpanded && hasCriterionScores && (
        <TableRow>
          <TableCell colSpan={TABLE_COLUMN_COUNT} className="bg-canvas p-0">
            <CriterionScoreBreakdown criterionScores={application.criterionScores} />
          </TableCell>
        </TableRow>
      )}
    </>
  )
}

export function ApplicationsTab({ jobId }: { jobId: string }) {
  const [sort, setSort] = useState<ApplicationSortOption>('total_score,desc')
  const [expandedApplicationIds, setExpandedApplicationIds] = useState<Set<string>>(new Set())

  const {
    data: applications,
    isLoading,
    isError,
    timedOut: listPollingTimedOut,
    resumePolling: resumeListPolling,
  } = useHrApplicationsQuery(jobId, sort)
  const createScoringRunMutation = useCreateScoringRunMutation(jobId)

  function toggleExpanded(applicationId: string) {
    setExpandedApplicationIds((previous) => {
      const next = new Set(previous)
      if (next.has(applicationId)) {
        next.delete(applicationId)
      } else {
        next.add(applicationId)
      }
      return next
    })
  }

  if (isLoading) {
    return <p className="p-6 text-sm text-ink-muted">Đang tải...</p>
  }
  if (isError || !applications) {
    return <p className="p-6 text-sm text-danger">Không tải được danh sách ứng viên, vui lòng thử lại.</p>
  }

  return (
    <div className="flex flex-col gap-4 p-6">
      <div className="flex items-center gap-2">
        <Label htmlFor="applications-sort" className="text-sm text-ink-muted">
          Sắp xếp theo
        </Label>
        <Select value={sort} onValueChange={(value) => setSort(value as ApplicationSortOption)}>
          <SelectTrigger id="applications-sort" className="w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="total_score,desc">Tổng điểm</SelectItem>
            <SelectItem value="applied_at,desc">Ngày nộp</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {/* timedOut: danh sach dung tu dong cap nhat sau 10 phut co don van dang "dang cham" khong
          doi (co the ket vinh vien do JVM backend restart giua chung, xem MAX_POLL_DURATION_MS
          trong queries.ts) - HR tu bam de kiem tra lai, khong tu dong lap lai vo han. */}
      {listPollingTimedOut && (
        <div className="flex items-center justify-between gap-3 rounded-(--radius-card) border border-line bg-canvas px-4 py-3 text-sm text-ink-muted">
          <span>Đã dừng tự động cập nhật do chờ quá lâu. Bấm "Tải lại" để kiểm tra trạng thái mới nhất.</span>
          <Button type="button" variant="outline" size="sm" onClick={() => resumeListPolling()}>
            <RotateCw className="h-3.5 w-3.5" aria-hidden="true" />
            Tải lại
          </Button>
        </div>
      )}

      {applications.length === 0 ? (
        <div className="flex flex-col items-center gap-1 rounded-(--radius-card) border border-line bg-surface py-12 text-center">
          <p className="text-sm text-ink-muted">Chưa có ứng viên nào nộp đơn cho tin tuyển dụng này.</p>
        </div>
      ) : (
        <div className="rounded-(--radius-card) border border-line bg-surface">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10" aria-hidden="true" />
                <TableHead>Ứng viên</TableHead>
                <TableHead>Ngày nộp</TableHead>
                <TableHead>Trạng thái CV</TableHead>
                <TableHead>Lượt chấm gần nhất</TableHead>
                <TableHead>Hạng</TableHead>
                <TableHead>Tổng điểm</TableHead>
                <TableHead className="text-right">Thao tác</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {applications.map((application) => (
                <ApplicationRow
                  key={application.id}
                  application={application}
                  isScoring={
                    createScoringRunMutation.isPending && createScoringRunMutation.variables === application.id
                  }
                  onScore={() => createScoringRunMutation.mutate(application.id)}
                  isExpanded={expandedApplicationIds.has(application.id)}
                  onToggleExpand={() => toggleExpanded(application.id)}
                />
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {createScoringRunMutation.isError && (
        <p className="text-sm text-danger">
          {extractErrorMessage(createScoringRunMutation.error, 'Tạo lượt chấm điểm thất bại, vui lòng thử lại.')}
        </p>
      )}
    </div>
  )
}

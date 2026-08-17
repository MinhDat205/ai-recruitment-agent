import { isAxiosError } from 'axios'
import { RotateCw } from 'lucide-react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { ParseStatusBadge } from '../resumes/ParseStatusBadge'
import { useCreateScoringRunMutation, useHrApplicationsQuery, useScoringRunsQuery } from './queries'
import { ScoringRunStatusBadge } from './ScoringRunStatusBadge'
import type { ApplicationHrListItem } from './types'

function formatAppliedAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
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
}: {
  application: ApplicationHrListItem
  onScore: () => void
  isScoring: boolean
}) {
  // Chi can goi lay chi tiet lot cham (criteriaScored/criteriaTotal, errorMessage) khi don NAY dang
  // co hoac da tung co mot lot cham - tranh goi thua cho don chua bao gio duoc bam "Cham diem ho so".
  const hasRun = application.latestScoringRunId !== null
  const {
    data: runs,
    timedOut: rowPollingTimedOut,
    resumePolling: resumeRowPolling,
  } = useScoringRunsQuery(application.id, hasRun)
  const latestRun = runs?.[0]
  const disabledReason = scoringDisabledReason(application)

  return (
    <TableRow>
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
  )
}

export function ApplicationsTab({ jobId }: { jobId: string }) {
  const {
    data: applications,
    isLoading,
    isError,
    timedOut: listPollingTimedOut,
    resumePolling: resumeListPolling,
  } = useHrApplicationsQuery(jobId)
  const createScoringRunMutation = useCreateScoringRunMutation(jobId)

  if (isLoading) {
    return <p className="p-6 text-sm text-ink-muted">Đang tải...</p>
  }
  if (isError || !applications) {
    return <p className="p-6 text-sm text-danger">Không tải được danh sách ứng viên, vui lòng thử lại.</p>
  }

  return (
    <div className="flex flex-col gap-4 p-6">
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
                <TableHead>Ứng viên</TableHead>
                <TableHead>Ngày nộp</TableHead>
                <TableHead>Trạng thái CV</TableHead>
                <TableHead>Lượt chấm gần nhất</TableHead>
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

import { useEffect, useId, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { AlertCircle, RotateCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatDateVi } from '../../lib/date'
import { extractErrorMessage } from '../../lib/httpError'
import { EMPTY_VALUE_PLACEHOLDER, formatTotalScore } from '../../lib/score'
import type { ParseStatus } from '../resumes/types'
import { CriterionScoreBreakdown } from '../scoring/CriterionScoreBreakdown'
import { useCreateScoringRunMutation, useScoringRunsQuery } from '../scoring/queries'
import { scoringDisabledReason } from '../scoring/scoringRules'
import { ScoringRunStatusBadge } from '../scoring/ScoringRunStatusBadge'
import {
  applicationDetailInvalidateKeys,
  applicationDetailKey,
  applicationExplanationKey,
  applicationScoresKey,
  useApplicationScoresQuery,
} from './queries'

// Trang thai "loi tai mot tab/phan" dung chung cua trang ho so don (UI.md muc 6): icon AlertCircle
// mau m3-error + chu on-surface + nut "Thử lại" chi tai lai phan do.
export function TabLoadError({ onRetry }: { onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-start gap-2">
      <p className="flex items-start gap-1.5 text-sm text-m3-on-surface">
        <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
        Không tải được dữ liệu, vui lòng thử lại.
      </p>
      <Button type="button" variant="outline" size="sm" onClick={onRetry}>
        Thử lại
      </Button>
    </div>
  )
}

export function SectionSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-hidden="true">
      <div className="h-4 w-1/3 animate-pulse rounded bg-m3-surface-container" />
      <div className="h-16 animate-pulse rounded bg-m3-surface-container" />
    </div>
  )
}

interface ScoreSectionProps {
  applicationId: string
  jobId: string
  resumeParseStatus: ParseStatus
}

// Khoi "Diem theo rubric" (R-T4..R-T7, R-S). Diem/hang/tieu chi lay tu E3 (lot DONE moi nhat, Backend
// tinh - frontend KHONG tinh tong hay hang, R-D4). Tien do lot moi nhat (bat ke trang thai) lay tu
// GET /scoring-runs co san (R-D3), poll bang useScoringRunsQuery.
//
// Nut "Chấm điểm hồ sơ" LUON hien o moi trang thai (R-S1); khoa/mo va cau ly do lay NGUYEN ket qua
// scoringDisabledReason (R-S2, R-S3) - khong co dieu kien rieng cua trang (khong khoa theo trang thai
// don, R-S5). Cau ly do hien thanh chu va gan vao nut bang aria-describedby, van giu title (UI.md muc 9).
export function ScoreSection({ applicationId, jobId, resumeParseStatus }: ScoreSectionProps) {
  const queryClient = useQueryClient()
  const reasonId = useId()
  const scoresQuery = useApplicationScoresQuery(applicationId)
  // untilFinal: poll tiep ca giai doan RUNNING da co finishedAt (cho tong hop) toi khi DONE/FAILED,
  // de biet luc tai lai diem (R-T14). Van dung sau 10 phut + nut "Tải lại" nhu danh sach.
  const runsQuery = useScoringRunsQuery(applicationId, true, { untilFinal: true })
  const createScoringRunMutation = useCreateScoringRunMutation(
    jobId,
    applicationDetailInvalidateKeys(applicationId, jobId),
  )

  const latestRun = runsQuery.data?.[0] ?? null

  // R-T14 - lot moi nhat doi trang thai (vd RUNNING -> co finishedAt -> DONE/FAILED) thi lam moi dau
  // trang, diem va giai thich. Bo qua lan dau co du lieu (chua co gi de so sanh). Chi doc ref trong
  // effect, khong setState.
  const latestRunSignature = runsQuery.data
    ? `${latestRun?.id ?? ''}|${latestRun?.status ?? ''}|${latestRun?.finishedAt ?? ''}`
    : null
  const previousSignatureRef = useRef<string | null>(null)
  useEffect(() => {
    if (latestRunSignature === null) {
      return
    }
    const previous = previousSignatureRef.current
    previousSignatureRef.current = latestRunSignature
    if (previous !== null && previous !== latestRunSignature) {
      queryClient.invalidateQueries({ queryKey: applicationDetailKey(applicationId) })
      queryClient.invalidateQueries({ queryKey: applicationScoresKey(applicationId) })
      queryClient.invalidateQueries({ queryKey: applicationExplanationKey(applicationId) })
    }
  }, [latestRunSignature, applicationId, queryClient])

  if (scoresQuery.isLoading || runsQuery.isLoading) {
    return (
      <section className="flex flex-col gap-3">
        <h2 className="text-m3-title-md text-m3-on-surface">Điểm theo rubric</h2>
        <SectionSkeleton />
      </section>
    )
  }

  // Chi coi la loi khi CHUA co du lieu - mot lan poll/tai lai loi thi giu du lieu dang hien.
  if (!scoresQuery.data || !runsQuery.data) {
    return (
      <section className="flex flex-col gap-3">
        <h2 className="text-m3-title-md text-m3-on-surface">Điểm theo rubric</h2>
        <TabLoadError
          onRetry={() => {
            scoresQuery.refetch()
            runsQuery.refetch()
          }}
        />
      </section>
    )
  }

  const scores = scoresQuery.data
  const disabledReason = scoringDisabledReason({
    resumeParseStatus,
    latestScoringRunStatus: latestRun?.status ?? null,
    latestScoringRunFinishedAt: latestRun?.finishedAt ?? null,
  })
  const hasDoneRun = scores.scoringRunId !== null
  // R-T7: lot moi nhat khac lot DONE dang cho diem (dang cham hoac FAILED) -> noi ro diem ben duoi la
  // cua lot hoan tat truoc do.
  const latestDiffersFromDone = hasDoneRun && latestRun !== null && latestRun.id !== scores.scoringRunId

  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-m3-title-md text-m3-on-surface">Điểm theo rubric</h2>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="w-full sm:w-auto"
          disabled={Boolean(disabledReason) || createScoringRunMutation.isPending}
          title={disabledReason}
          aria-describedby={disabledReason ? reasonId : undefined}
          onClick={() => createScoringRunMutation.mutate(applicationId)}
        >
          Chấm điểm hồ sơ
        </Button>
      </div>
      {disabledReason && (
        <p id={reasonId} className="text-sm text-m3-on-surface-variant">
          {disabledReason}
        </p>
      )}
      {createScoringRunMutation.isError && (
        <div role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          <p>
            {extractErrorMessage(createScoringRunMutation.error, 'Tạo lượt chấm điểm thất bại, vui lòng thử lại.')}
          </p>
        </div>
      )}

      {/* Vung tien do/trang thai cham (R-T4..R-T7) - aria-live de doc khi poll doi trang thai. */}
      <div aria-live="polite" className="flex flex-col items-start gap-2">
        {!hasDoneRun && latestRun === null && <p className="text-sm text-m3-on-surface">Đơn này chưa được chấm điểm.</p>}
        {(!hasDoneRun || latestDiffersFromDone) && latestRun !== null && (
          <>
            <ScoringRunStatusBadge
              status={latestRun.status}
              finishedAt={latestRun.finishedAt}
              criteriaScored={latestRun.criteriaScored}
              criteriaTotal={latestRun.criteriaTotal}
            />
            {/* errorMessage (ma loi da chuan hoa) NGUYEN VAN, mau on-surface: cham loi la loi ky thuat,
                khong phai nhan xet ve ung vien (UI.md 5d). */}
            {latestRun.status === 'FAILED' && latestRun.errorMessage && (
              <p className="whitespace-normal break-words text-sm text-m3-on-surface">{latestRun.errorMessage}</p>
            )}
          </>
        )}
        {latestDiffersFromDone && scores.scoredAt && (
          <p className="text-sm text-m3-on-surface">
            Lượt chấm mới nhất chưa hoàn tất hoặc không thành công. Điểm bên dưới là của lượt hoàn tất ngày{' '}
            {formatDateVi(scores.scoredAt)}.
          </p>
        )}
      </div>

      {/* timedOut: dung tu dong cap nhat sau 10 phut (xem MAX_POLL_DURATION_MS, features/scoring/queries.ts)
          - HR tu bam de kiem tra lai. */}
      {runsQuery.timedOut && (
        <div className="flex flex-col gap-2 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface-container px-4 py-3 text-sm text-m3-on-surface sm:flex-row sm:items-center sm:justify-between">
          <span>Đã dừng tự động cập nhật do chờ quá lâu.</span>
          <Button type="button" variant="outline" size="sm" onClick={() => runsQuery.resumePolling()}>
            <RotateCw className="h-3.5 w-3.5" aria-hidden="true" />
            Tải lại
          </Button>
        </div>
      )}

      {/* R-T7, R-G2 - tong diem va hang nam TRONG khung danh sach tieu chi, mot mau mot co cho moi gia
          tri (khong to mau/in dam theo diem hay hang). */}
      {hasDoneRun && (
        <div className="rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface">
          <div className="flex flex-col gap-1 px-4 pt-4">
            <p className="flex flex-col text-m3-title-md text-m3-on-surface sm:flex-row sm:gap-2">
              <span>Tổng điểm {formatTotalScore(scores.totalScore)} / 100</span>
              <span className="hidden sm:inline" aria-hidden="true">
                ·
              </span>
              <span>Hạng {scores.rank === null ? EMPTY_VALUE_PLACEHOLDER : scores.rank}</span>
            </p>
            {scores.scoredAt && (
              <p className="text-sm text-m3-on-surface-variant">
                Hệ thống tính theo trọng số rubric từ lượt chấm hoàn tất ngày {formatDateVi(scores.scoredAt)}.
              </p>
            )}
          </div>
          <CriterionScoreBreakdown criterionScores={scores.criterionScores} />
        </div>
      )}
    </section>
  )
}

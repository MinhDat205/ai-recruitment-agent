import { isAxiosError } from 'axios'
import { useState } from 'react'
import { Download, FileText, RotateCw } from 'lucide-react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Sheet, SheetBody, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { ParseStatusBadge } from '../resumes/ParseStatusBadge'
import { downloadApplicationResumeRequest } from './api'
import { CriterionScoreBreakdown } from './CriterionScoreBreakdown'
import { ExplanationReport } from './ExplanationReport'
import { useCreateScoringRunMutation, useHrApplicationsQuery, useScoringRunsQuery } from './queries'
import { ScoringRunStatusBadge } from './ScoringRunStatusBadge'
import type { ApplicationHrListItem, ApplicationSortOption } from './types'

// Dau gach ngang trung tinh cho o CHUA CO gia tri (don chua cham / lot FAILED / dang cham dang) -
// KHONG hien "0" (se hieu nham la diem that bang khong) hay chu "Chua cham" (trung lap va co the
// mau thuan voi tin hieu tien do o duoi so diem, xem ScoringProgressHint). O nay chi tra loi "co
// gia tri hay khong", "vi sao" da co ScoringProgressHint tra loi.
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

// Dieu kien disable nut "Xem CV goc" (Dot 5b, yeu cau bat buoc) - CHI kiem CV da parse xong hay
// chua, KHONG dung chung dieu kien voi scoringDisabledReason (nut cham diem con chan them ca
// truong hop "dang co lot cham chua hoan tat" - khong lien quan gi toi viec doc file CV goc, hai
// nut kiem tra hai dieu kien doc lap nhau). Ve mat ky thuat file CV goc luon ton tai tu luc upload
// bat ke parse xong hay chua (ResumeService.upload luu file TRUOC khi kich hoat parse nen), nhung
// dieu kien nay la lua chon UX co chu dich theo dung yeu cau da giao, khong phai rang buoc ky
// thuat. "hay khong tai duoc" (ve con lai cua yeu cau) KHONG the biet truoc luc render (chi lo ra
// khi thuc su goi API, vd file mat tren dia - xem ResumeHrService) - xu ly bang thong bao loi hien
// SAU KHI bam (xem handleDownloadResume/downloadError), khong phai disable tinh o day.
function resumeDownloadDisabledReason(application: ApplicationHrListItem): string | undefined {
  if (application.resumeParseStatus !== 'DONE') {
    return 'CV của ứng viên chưa được AI trích xuất xong, vui lòng chờ xử lý xong rồi thử lại.'
  }
  return undefined
}

// Tin hieu tien do NGAN GON duoi o Tong diem (Dot 5b) - thay cho cot "Luot cham gan nhat" rieng da
// bo di de bang gon lai. CHI hien khi co gi do dang xu ly hoac that bai (PENDING/RUNNING/FAILED) -
// bo qua ca hai truong hop con lai (null: chua tung cham, DONE: da co Hang/Tong diem tren cung dong
// roi, nhac lai "Da hoan tat" o day la thua va lam roi bang). Van la thanh phan TRUNG TINH y het
// ScoringRunStatusBadge goc (xem comment trong file do) - Dot 5b chi doi VI TRI, khong doi noi dung
// hay mau sac.
function ScoringProgressHint({
  application,
  criteriaScored,
  criteriaTotal,
  errorMessage,
  timedOut,
  onResume,
}: {
  application: ApplicationHrListItem
  criteriaScored: number
  criteriaTotal: number
  errorMessage: string | null | undefined
  timedOut: boolean
  onResume: () => void
}) {
  const status = application.latestScoringRunStatus
  if (status === null || status === 'DONE') {
    return null
  }
  return (
    <div className="flex flex-col items-start gap-1">
      <ScoringRunStatusBadge
        status={status}
        finishedAt={application.latestScoringRunFinishedAt}
        criteriaScored={criteriaScored}
        criteriaTotal={criteriaTotal}
      />
      {status === 'FAILED' && errorMessage && <p className="text-xs text-ink-muted">{errorMessage}</p>}
      {/* timedOut: lot cham nay dung tu dong cap nhat sau 10 phut khong doi (co the ket vinh vien do
          JVM backend restart giua chung, xem MAX_POLL_DURATION_MS) - HR tu bam de kiem tra lai,
          khong tu dong lap lai vo han. */}
      {timedOut && (
        <button
          type="button"
          className="inline-flex items-center gap-1 text-xs font-medium text-brand hover:underline"
          onClick={onResume}
        >
          <RotateCw className="h-3 w-3" aria-hidden="true" />
          Tải lại
        </button>
      )}
    </div>
  )
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
  const [sheetOpen, setSheetOpen] = useState(false)
  const [isDownloadingResume, setDownloadingResume] = useState(false)
  const [resumeDownloadError, setResumeDownloadError] = useState<string | null>(null)

  // Chi can goi lay chi tiet lot cham (criteriaScored/criteriaTotal, errorMessage) khi don NAY dang
  // co hoac da tung co mot lot cham - tranh goi thua cho don chua bao gio duoc bam "Cham diem ho
  // so". Hook nay khong phu thuoc sheetOpen - tiep tuc poll binh thuong du khu vuc chi tiet dang mo
  // hay dong (yeu cau Dot 5b).
  const hasRun = application.latestScoringRunId !== null
  const {
    data: runs,
    timedOut: rowPollingTimedOut,
    resumePolling: resumeRowPolling,
  } = useScoringRunsQuery(application.id, hasRun)
  const latestRun = runs?.[0]
  const disabledReason = scoringDisabledReason(application)
  const resumeDisabledReason = resumeDownloadDisabledReason(application)
  const hasCriterionScores = application.criterionScores.length > 0
  // Bao cao tong hop (D4/FR-H06) co the co du lieu de hien (explanation hoac tin hieu PENDING/
  // FAILED) ke ca khi criterionScores rong (ly thuyet: rubric khong co tieu chi nao) - nut "Xem
  // danh gia cua AI" van phai mo duoc trong truong hop do, khong chi phu thuoc hasCriterionScores.
  const hasExplanationInfo = application.explanation !== null || application.explanationStatus !== null
  const hasEvaluationToShow = hasCriterionScores || hasExplanationInfo

  // Blob download qua axios (KHONG phai <a href>/window.open truc tiep toi URL backend) - endpoint
  // yeu cau header Authorization (Bearer token), the <a href> thuong khong gan duoc header nay vao
  // request (mau y het handleDownload trong features/resumes/ResumeList.tsx, cung ly do). Loi tra
  // ve khi responseType 'blob' cung la Blob (khong phai JSON da parse) nen extractErrorMessage se
  // luon roi ve cau fallback - gioi han da biet va chap nhan duoc, giong y het ResumeList.
  async function handleDownloadResume() {
    setResumeDownloadError(null)
    setDownloadingResume(true)
    try {
      const { blob, fileName } = await downloadApplicationResumeRequest(application.id)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = fileName
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    } catch (err) {
      setResumeDownloadError(extractErrorMessage(err, 'Tải CV gốc thất bại, vui lòng thử lại.'))
    } finally {
      setDownloadingResume(false)
    }
  }

  return (
    <TableRow>
      <TableCell>{application.candidateName}</TableCell>
      <TableCell className="text-ink-muted">{formatAppliedAt(application.appliedAt)}</TableCell>
      <TableCell>
        <ParseStatusBadge status={application.resumeParseStatus} />
      </TableCell>
      {/* Hang/Tong diem: so trung tinh, CUNG mau/kieu chu voi cac cot khac (text-ink) - KHONG to mau
          theo nguong, KHONG in dam du la hang 1. Cam tuyet doi theo srs-guard. */}
      <TableCell className="text-ink">{formatRank(application.rank)}</TableCell>
      <TableCell className="text-ink">
        <div className="flex flex-col gap-1">
          <span>{formatTotalScore(application.totalScore)}</span>
          <ScoringProgressHint
            application={application}
            criteriaScored={latestRun?.criteriaScored ?? 0}
            criteriaTotal={latestRun?.criteriaTotal ?? 0}
            errorMessage={latestRun?.errorMessage}
            timedOut={rowPollingTimedOut}
            onResume={() => resumeRowPolling()}
          />
        </div>
      </TableCell>
      <TableCell className="text-right">
        <div className="flex flex-col items-end gap-1.5">
          <div className="flex items-center justify-end gap-2">
            {/* Xem CV goc (FR-H06, Dot 5b): dat CANH nut "Xem danh gia cua AI" theo dung yeu cau -
                phuc vu doi chieu evidence trong bao cao AI voi van ban CV that (nguyen tac
                Explainable AI, xem ResumeHrService). Tai xuong (khong mo tab moi) - xem comment
                handleDownloadResume/downloadApplicationResumeRequest ve ly do bat buoc ky thuat
                (Authorization header). */}
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={Boolean(resumeDisabledReason) || isDownloadingResume}
              title={resumeDisabledReason}
              onClick={handleDownloadResume}
            >
              <Download className="h-3.5 w-3.5" aria-hidden="true" />
              Xem CV gốc
            </Button>
            <Sheet open={sheetOpen} onOpenChange={setSheetOpen}>
              <SheetTrigger asChild>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={!hasEvaluationToShow}
                  title={hasEvaluationToShow ? undefined : 'Đơn này chưa có kết quả chấm điểm để xem.'}
                >
                  <FileText className="h-3.5 w-3.5" aria-hidden="true" />
                  Xem đánh giá của AI
                </Button>
              </SheetTrigger>
              {/* Panel rong (sm:max-w-xl md:max-w-2xl, xem components/ui/sheet.tsx) thay vi mo rong
                  ngay trong bang (thiet ke cu, Dot 5) - noi dung nay (reasoning/evidence/summary
                  tieng Viet dai) khi bi ep vao chieu rong cot bang se hoac tran ngang hoac xuong
                  dong gay lien tuc. Dung MOT panel rieng, doc lap chieu rong voi bang, giai quyet
                  tan goc thay vi vá bang overflow-wrap. Noi dung ben trong GIU NGUYEN, khong them
                  du lieu moi - dung y het CriterionScoreBreakdown/ExplanationReport cua Dot 5. */}
              <SheetContent>
                <SheetHeader>
                  <SheetTitle>Đánh giá của AI — {application.candidateName}</SheetTitle>
                  <SheetDescription>
                    Điểm từng tiêu chí và báo cáo tổng hợp từ lượt chấm điểm gần nhất đã hoàn tất.
                  </SheetDescription>
                </SheetHeader>
                <SheetBody>
                  {hasCriterionScores && <CriterionScoreBreakdown criterionScores={application.criterionScores} />}
                  <ExplanationReport
                    explanation={application.explanation}
                    explanationStatus={application.explanationStatus}
                  />
                </SheetBody>
              </SheetContent>
            </Sheet>
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
          </div>
          {resumeDownloadError && <p className="text-xs text-danger">{resumeDownloadError}</p>}
        </div>
      </TableCell>
    </TableRow>
  )
}

export function ApplicationsTab({ jobId }: { jobId: string }) {
  const [sort, setSort] = useState<ApplicationSortOption>('total_score,desc')

  const {
    data: applications,
    isLoading,
    isError,
    timedOut: listPollingTimedOut,
    resumePolling: resumeListPolling,
  } = useHrApplicationsQuery(jobId, sort)
  const createScoringRunMutation = useCreateScoringRunMutation(jobId)

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
                <TableHead>Ứng viên</TableHead>
                <TableHead>Ngày nộp</TableHead>
                <TableHead>Trạng thái CV</TableHead>
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

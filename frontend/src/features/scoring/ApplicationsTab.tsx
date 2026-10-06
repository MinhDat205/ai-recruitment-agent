import { useState } from 'react'
import { AlertCircle, Download, FileText, RotateCw } from 'lucide-react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Sheet, SheetBody, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { ApplicationStatusBadge } from '../applications/ApplicationStatusBadge'
import {
  ApplicationStatusConfirmDialog,
  type ApplicationStatusConfirmTarget,
} from '../applications/ApplicationStatusConfirmDialog'
import type { ApplicationStatus } from '../applications/types'
import { InterviewInvitationDialog } from '../interviewinvitation/InterviewInvitationDialog'
import { extractErrorMessage } from '../../lib/httpError'
import { EMPTY_VALUE_PLACEHOLDER, formatTotalScore } from '../../lib/score'
import { ParseStatusBadge } from '../resumes/ParseStatusBadge'
import { CriterionScoreBreakdown } from './CriterionScoreBreakdown'
import { downloadApplicationResume } from './downloadApplicationResume'
import { ExplanationReport } from './ExplanationReport'
import { useCreateScoringRunMutation, useHrApplicationsQuery, useScoringRunsQuery } from './queries'
import { scoringDisabledReason } from './scoringRules'
import { ScoringRunStatusBadge } from './ScoringRunStatusBadge'
import type { ApplicationHrListItem, ApplicationSortOption } from './types'

// EMPTY_VALUE_PLACEHOLDER/formatTotalScore (lib/score.ts, tach o FR-H09): dau gach ngang trung tinh
// cho o CHUA CO gia tri - KHONG hien "0" hay chu "Chua cham" (trung lap va co the mau thuan voi tin
// hieu tien do o duoi so diem, xem ScoringProgressHint). O nay chi tra loi "co gia tri hay khong",
// "vi sao" da co ScoringProgressHint tra loi.

function formatAppliedAt(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatRank(rank: number | null): string {
  return rank === null ? EMPTY_VALUE_PLACEHOLDER : String(rank)
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

// FR-H07 (E1, Dot 3) - nut hanh dong hop le theo DUNG may trang thai backend
// (ApplicationStatusService.ALLOWED_TRANSITIONS): PENDING -> {INTERVIEW_INVITED, REJECTED},
// INTERVIEW_INVITED -> {HIRED, REJECTED}. Day CHI la tien dung UI (an nut sai luong) - backend van
// la chot chan that su, goi sai van bi 400 du UI co an nut hay khong (muc 4 de bai). KHONG doc
// totalScore/rank/criterionScores o day - nut hien/an CHI phu thuoc status, khong phu thuoc diem so.
function nextActionsFor(status: ApplicationStatus): { canInvite: boolean; canReject: boolean; canHire: boolean } {
  if (status === 'PENDING') {
    return { canInvite: true, canReject: true, canHire: false }
  }
  if (status === 'INTERVIEW_INVITED') {
    return { canInvite: false, canReject: true, canHire: true }
  }
  return { canInvite: false, canReject: false, canHire: false }
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
      {status === 'FAILED' && errorMessage && (
        <p className="whitespace-normal break-words text-xs text-m3-on-surface-variant">{errorMessage}</p>
      )}
      {/* timedOut: lot cham nay dung tu dong cap nhat sau 10 phut khong doi (co the ket vinh vien do
          JVM backend restart giua chung, xem MAX_POLL_DURATION_MS) - HR tu bam de kiem tra lai,
          khong tu dong lap lai vo han. */}
      {timedOut && (
        <button
          type="button"
          className="inline-flex items-center gap-1 text-xs font-medium text-m3-on-primary-container hover:underline"
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
  onInvite,
  onReject,
  onHire,
}: {
  application: ApplicationHrListItem
  onScore: () => void
  isScoring: boolean
  onInvite: () => void
  onReject: () => void
  onHire: () => void
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
  const actions = nextActionsFor(application.status)

  // Tai blob dung chung voi trang ho so don (downloadApplicationResume.ts, FR-H09 R-C4) - xem comment
  // o do ve ly do bat buoc tai qua axios. Dieu kien khoa nut van o day (resumeDownloadDisabledReason).
  async function handleDownloadResume() {
    setResumeDownloadError(null)
    setDownloadingResume(true)
    try {
      await downloadApplicationResume(application.id)
    } catch (err) {
      setResumeDownloadError(extractErrorMessage(err, 'Tải CV gốc thất bại, vui lòng thử lại.'))
    } finally {
      setDownloadingResume(false)
    }
  }

  return (
    <TableRow>
      <TableCell className="whitespace-normal break-words">{application.candidateName}</TableCell>
      <TableCell className="text-m3-on-surface-variant">{formatAppliedAt(application.appliedAt)}</TableCell>
      <TableCell>
        <ParseStatusBadge status={application.resumeParseStatus} />
      </TableCell>
      <TableCell>
        <ApplicationStatusBadge status={application.status} />
      </TableCell>
      {/* Hang/Tong diem: so trung tinh, CUNG mau/kieu chu voi cac cot khac (text-m3-on-surface) - KHONG to mau
          theo nguong, KHONG in dam du la hang 1. Cam tuyet doi theo srs-guard. */}
      <TableCell className="text-m3-on-surface">{formatRank(application.rank)}</TableCell>
      <TableCell className="text-m3-on-surface">
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
          <div className="flex flex-wrap items-center justify-end gap-2">
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
                {/* FR-H07 (E1, Dot 3): nut nay tung bi disable khi chua co ket qua cham diem (Dot
                    5b) - BO disable o day vi Sheet gio la loi vao CHUNG cho ca "xem danh gia" LAN
                    "hai nut hanh dong" (Moi phong van/Tu choi/Trung tuyen). CLAUDE.md muc 7 cam ro:
                    "khong duoc them rang buoc kieu chi cho moi phong van khi da cham diem xong" -
                    disable o day se vo tinh tao dung rang buoc do. Truong hop chua co danh gia hien
                    thong bao trung tinh BEN TRONG Sheet (xem hasEvaluationToShow ben duoi), khong
                    con chan tu nut trigger. */}
                <Button type="button" variant="outline" size="sm">
                  <FileText className="h-3.5 w-3.5" aria-hidden="true" />
                  Xem hồ sơ
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
                  <SheetTitle>Hồ sơ ứng viên — {application.candidateName}</SheetTitle>
                  <SheetDescription>
                    Điểm từng tiêu chí và báo cáo tổng hợp từ lượt chấm điểm gần nhất đã hoàn tất.
                  </SheetDescription>
                  <ApplicationStatusBadge status={application.status} />
                </SheetHeader>
                <SheetBody>
                  {hasEvaluationToShow ? (
                    <>
                      {hasCriterionScores && <CriterionScoreBreakdown criterionScores={application.criterionScores} />}
                      <ExplanationReport
                        explanation={application.explanation}
                        explanationStatus={application.explanationStatus}
                      />
                    </>
                  ) : (
                    <p className="p-4 text-sm text-m3-on-surface-variant">Đơn này chưa có kết quả chấm điểm để xem.</p>
                  )}
                </SheetBody>
                {/* FR-H07 (E1, Dot 3) - hai hanh dong theo DUNG trang thai hien tai cua don (xem
                    nextActionsFor). CHI phu thuoc application.status, KHONG doc totalScore/rank o
                    day - an nut chi la tien dung UI, backend van la chot chan that (muc 4 de bai). */}
                {(actions.canInvite || actions.canReject || actions.canHire) && (
                  <div className="flex justify-end gap-2 border-t border-m3-outline-variant px-6 py-4">
                    {actions.canInvite && (
                      <Button type="button" variant="outline" onClick={onInvite}>
                        Mời phỏng vấn
                      </Button>
                    )}
                    {actions.canHire && (
                      <Button type="button" variant="outline" onClick={onHire}>
                        Trúng tuyển
                      </Button>
                    )}
                    {actions.canReject && (
                      <Button type="button" variant="outline" onClick={onReject}>
                        Từ chối
                      </Button>
                    )}
                  </div>
                )}
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
          {/* Nam trong hang bang (hover #F8F8F8): chu do chi dat 4.46:1, tin hieu loi giu bang icon. */}
          {resumeDownloadError && (
            <div role="alert" className="flex items-start gap-1.5 text-xs text-m3-on-surface">
              <AlertCircle className="mt-px h-3.5 w-3.5 shrink-0 text-m3-error" aria-hidden="true" />
              <p>{resumeDownloadError}</p>
            </div>
          )}
        </div>
      </TableCell>
    </TableRow>
  )
}

export function ApplicationsTab({ jobId }: { jobId: string }) {
  const [sort, setSort] = useState<ApplicationSortOption>('total_score,desc')
  const [inviteTarget, setInviteTarget] = useState<ApplicationHrListItem | null>(null)
  const [confirmTarget, setConfirmTarget] = useState<ApplicationStatusConfirmTarget | null>(null)

  const {
    data: applications,
    isLoading,
    isError,
    timedOut: listPollingTimedOut,
    resumePolling: resumeListPolling,
  } = useHrApplicationsQuery(jobId, sort)
  const createScoringRunMutation = useCreateScoringRunMutation(jobId)

  if (isLoading) {
    return <p className="p-6 text-sm text-m3-on-surface-variant">Đang tải...</p>
  }
  if (isError || !applications) {
    return <p className="p-6 text-sm text-m3-error">Không tải được danh sách ứng viên, vui lòng thử lại.</p>
  }

  return (
    <div className="flex flex-col gap-4 p-6">
      <div className="flex items-center gap-2">
        <Label htmlFor="applications-sort" className="text-sm text-m3-on-surface-variant">
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
        <div className="flex items-center justify-between gap-3 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface-container px-4 py-3 text-sm text-m3-on-surface">
          <span>Đã dừng tự động cập nhật do chờ quá lâu. Bấm "Tải lại" để kiểm tra trạng thái mới nhất.</span>
          <Button type="button" variant="outline" size="sm" onClick={() => resumeListPolling()}>
            <RotateCw className="h-3.5 w-3.5" aria-hidden="true" />
            Tải lại
          </Button>
        </div>
      )}

      {applications.length === 0 ? (
        <div className="flex flex-col items-center gap-1 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface py-12 text-center">
          <p className="text-sm text-m3-on-surface-variant">Chưa có ứng viên nào nộp đơn cho tin tuyển dụng này.</p>
        </div>
      ) : (
        <div className="rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Ứng viên</TableHead>
                <TableHead>Ngày nộp</TableHead>
                <TableHead>Trạng thái CV</TableHead>
                <TableHead>Trạng thái đơn</TableHead>
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
                  onInvite={() => setInviteTarget(application)}
                  onReject={() => setConfirmTarget({ application, targetStatus: 'REJECTED' })}
                  onHire={() => setConfirmTarget({ application, targetStatus: 'HIRED' })}
                />
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {createScoringRunMutation.isError && (
        <p className="text-sm text-m3-error">
          {extractErrorMessage(createScoringRunMutation.error, 'Tạo lượt chấm điểm thất bại, vui lòng thử lại.')}
        </p>
      )}

      <InterviewInvitationDialog
        application={inviteTarget}
        jobId={jobId}
        onOpenChange={(open) => !open && setInviteTarget(null)}
      />

      {/* Hop xac nhan Tu choi/Trung tuyen da chuyen sang features/applications (FR-H09 R-A4), noi dung
          chu va hanh vi giu nguyen. */}
      <ApplicationStatusConfirmDialog
        target={confirmTarget}
        jobId={jobId}
        onOpenChange={(open) => !open && setConfirmTarget(null)}
      />
    </div>
  )
}

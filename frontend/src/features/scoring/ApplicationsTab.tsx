import { useState } from 'react'
import { AlertCircle, Download, FileText, Info, RotateCw } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApplicationStatusBadge } from '../applications/ApplicationStatusBadge'
import { BLIND_REVIEW_TEXT, downloadErrorMessage } from '../blindReview/blindReviewLabels'
import { useHrJobQuery } from '../jobs/ownerQueries'
import { MAX_COMPARE, MIN_COMPARE } from '../comparison/compareLimits'
import { COMPARE_SELECTION_TEXT } from '../comparison/comparisonLabels'
import { extractErrorMessage } from '../../lib/httpError'
import { EMPTY_VALUE_PLACEHOLDER, formatTotalScore } from '../../lib/score'
import { ParseStatusBadge } from '../resumes/ParseStatusBadge'
import { downloadApplicationResume } from './downloadApplicationResume'
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
  // FR-H16 R-V1 - backend tra 409 khi tai CV goc cua don dang an danh; khoa nut cho ro ly do (backend van la chot chan).
  if (application.identityMasked) {
    return BLIND_REVIEW_TEXT.downloadBlocked
  }
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

// FR-H14 R-L1 - ly do khoa o chon, theo thu tu uu tien: chua cham xong -> khac chu ky voi don chon dau -> da du
// MAX_COMPARE. Don DANG duoc chon luon bo chon duoc (khong khoa). null = o bat. Backend van la chot chan (R-S1,
// R-S4, R-S5); day chi de bao som.
type SelectionBlockReason = 'notScored' | 'differentCycle' | 'limit'

function selectionBlockReason(
  application: ApplicationHrListItem,
  selectedIds: string[],
  firstSelectedCycle: number | null,
): SelectionBlockReason | null {
  if (selectedIds.includes(application.id)) {
    return null
  }
  if (application.totalScore === null) {
    return 'notScored'
  }
  if (firstSelectedCycle !== null && application.recruitmentCycle !== firstSelectedCycle) {
    return 'differentCycle'
  }
  if (selectedIds.length >= MAX_COMPARE) {
    return 'limit'
  }
  return null
}

// Id cua cau "Da chon du {MAX_COMPARE} don." o thanh chon - o khoa vi ly do nay tro aria-describedby ve day thay vi
// lap cau o tung dong (UI.md 4b).
const LIMIT_NOTE_ID = 'compare-selection-limit-note'

// FR-H16 UI.md muc 9 - nut khoa dung disabled + aria-describedby tro cau ly do (khong chi title).
function resumeReasonId(applicationId: string): string {
  return `resume-download-reason-${applicationId}`
}

function selectionReasonId(applicationId: string): string {
  return `compare-selection-reason-${applicationId}`
}

interface RowSelection {
  checked: boolean
  blockReason: SelectionBlockReason | null
  onToggle: () => void
}

function ApplicationRow({
  application,
  onScore,
  isScoring,
  selection,
}: {
  application: ApplicationHrListItem
  onScore: () => void
  isScoring: boolean
  selection: RowSelection
}) {
  const [isDownloadingResume, setDownloadingResume] = useState(false)
  const [resumeDownloadError, setResumeDownloadError] = useState<string | null>(null)

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
  const resumeDisabledReason = resumeDownloadDisabledReason(application)

  // Tai blob dung chung voi trang ho so don (downloadApplicationResume.ts, FR-H09 R-C4) - xem comment
  // o do ve ly do bat buoc tai qua axios. Dieu kien khoa nut van o day (resumeDownloadDisabledReason).
  async function handleDownloadResume() {
    setResumeDownloadError(null)
    setDownloadingResume(true)
    try {
      await downloadApplicationResume(application.id)
    } catch (err) {
      setResumeDownloadError(downloadErrorMessage(err, 'Tải CV gốc thất bại, vui lòng thử lại.'))
    } finally {
      setDownloadingResume(false)
    }
  }

  // Cau ly do hien thanh chu duoi ten ung vien (khong chi title); ly do "du MAX_COMPARE" khong lap o dong - tro ve
  // cau o thanh chon.
  const rowReasonText =
    selection.blockReason === 'notScored'
      ? COMPARE_SELECTION_TEXT.notScored
      : selection.blockReason === 'differentCycle'
        ? COMPARE_SELECTION_TEXT.differentCycle
        : null
  const describedBy =
    selection.blockReason === 'limit' ? LIMIT_NOTE_ID : rowReasonText ? selectionReasonId(application.id) : undefined

  return (
    <TableRow>
      {/* FR-H14 - o chon dau bang. Label 48x48 boc o 16px: vung cham >= 48px (UI_GUIDE 1h); khoa = disabled +
          aria-describedby (R-L2). */}
      <TableCell className="w-12 p-0 align-top">
        <label className="flex h-12 w-12 cursor-pointer items-center justify-center has-disabled:cursor-not-allowed">
          <Checkbox
            checked={selection.checked}
            disabled={selection.blockReason !== null}
            onCheckedChange={selection.onToggle}
            aria-label={COMPARE_SELECTION_TEXT.checkboxLabel(application.candidateName)}
            aria-describedby={describedBy}
            className="border-m3-outline disabled:opacity-38"
          />
        </label>
      </TableCell>
      <TableCell className="whitespace-normal break-words">
        {application.candidateName}
        {rowReasonText && (
          <p id={selectionReasonId(application.id)} className="mt-1 text-m3-body-sm text-m3-on-surface-variant">
            {rowReasonText}
          </p>
        )}
      </TableCell>
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
            {/* Xem CV goc (FR-H06, Dot 5b): dat CANH nut "Xem ho so" - phuc vu doi chieu evidence
                trong bao cao AI voi van ban CV that (nguyen tac Explainable AI, xem ResumeHrService).
                Tai xuong (khong mo tab moi) - xem comment handleDownloadResume/
                downloadApplicationResumeRequest ve ly do bat buoc ky thuat (Authorization header). */}
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={Boolean(resumeDisabledReason) || isDownloadingResume}
              title={resumeDisabledReason}
              aria-describedby={resumeDisabledReason ? resumeReasonId(application.id) : undefined}
              onClick={handleDownloadResume}
            >
              <Download className="h-3.5 w-3.5" aria-hidden="true" />
              Xem CV gốc
            </Button>
            {resumeDisabledReason && (
              <span id={resumeReasonId(application.id)} className="sr-only">
                {resumeDisabledReason}
              </span>
            )}
            {/* FR-H09 R-E1, R-P3 - lien ket toi trang ho so don (diem, giai thich, lich su va nut quyet
                dinh FR-H07 chi con o trang do). Giu vi tri, icon va kieu cua nut "Xem ho so" cu; khong
                khoa theo ket qua cham (CLAUDE.md muc 7: khong rang buoc "chi moi phong van khi da cham"). */}
            <Button asChild variant="outline" size="sm">
              <Link to={`/hr/applications/${application.id}`}>
                <FileText className="h-3.5 w-3.5" aria-hidden="true" />
                Xem hồ sơ
              </Link>
            </Button>
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

// FR-H14 (UI.md 4b, 4d) - thanh chon nam TRONG luong trang (khong fixed/sticky). < sm: xep doc, hai nut chia doi
// hang, cao >= 48px.
function CompareSelectionBar({
  selectedCount,
  onClear,
  onCompare,
}: {
  selectedCount: number
  onClear: () => void
  onCompare: () => void
}) {
  const canCompare = selectedCount >= MIN_COMPARE && selectedCount <= MAX_COMPARE
  return (
    <div className="flex flex-col gap-2 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex flex-col gap-0.5">
        <p aria-live="polite" className="text-m3-body-md text-m3-on-surface">
          {COMPARE_SELECTION_TEXT.selectedCount(selectedCount)}
        </p>
        {selectedCount < MIN_COMPARE && (
          <p className="text-m3-body-sm text-m3-on-surface-variant">{COMPARE_SELECTION_TEXT.needMore}</p>
        )}
        {selectedCount >= MAX_COMPARE && (
          <p id={LIMIT_NOTE_ID} className="text-m3-body-sm text-m3-on-surface-variant">
            {COMPARE_SELECTION_TEXT.limitReached}
          </p>
        )}
      </div>
      <div className="flex gap-2">
        {selectedCount > 0 && (
          <Button type="button" variant="outline" className="min-h-12 flex-1 sm:min-h-0 sm:flex-none" onClick={onClear}>
            {COMPARE_SELECTION_TEXT.clear}
          </Button>
        )}
        <Button
          type="button"
          className="min-h-12 flex-1 bg-m3-primary text-m3-on-primary hover:bg-m3-primary/90 sm:min-h-0 sm:flex-none"
          disabled={!canCompare}
          onClick={onCompare}
        >
          {COMPARE_SELECTION_TEXT.compare}
        </Button>
      </div>
    </div>
  )
}

export function ApplicationsTab({ jobId }: { jobId: string }) {
  const [sort, setSort] = useState<ApplicationSortOption>('total_score,desc')
  // FR-H14 R-L3 - id don theo THU TU TICK (cot trang so sanh theo dung thu tu nay, R-O1). State nam o ApplicationsTab:
  // doi "Sap xep theo" chi doi queryKey (component KHONG remount, khong doi key) va polling chi lam moi du lieu nen
  // lua chon giu nguyen. Roi tab/tai lai trang thi mat (chap nhan theo R-L3).
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const navigate = useNavigate()

  const {
    data: applications,
    isLoading,
    isError,
    timedOut: listPollingTimedOut,
    resumePolling: resumeListPolling,
  } = useHrApplicationsQuery(jobId, sort)
  const createScoringRunMutation = useCreateScoringRunMutation(jobId)
  // FR-H16 UI.md 4b - nguon cau thong tin la co cua Job (query san co cua trang sua Job, cung khoa nen khong goi them).
  const { data: job } = useHrJobQuery(jobId)

  if (isLoading) {
    return <p className="p-6 text-sm text-m3-on-surface-variant">Đang tải...</p>
  }
  if (isError || !applications) {
    return <p className="p-6 text-sm text-m3-error">Không tải được danh sách ứng viên, vui lòng thử lại.</p>
  }

  // Chu ky cua don DUNG DAU mang lua chon (bo chon don dau thi don ke tiep thanh don chon dau).
  const firstSelectedCycle =
    selectedIds.length === 0
      ? null
      : (applications.find((application) => application.id === selectedIds[0])?.recruitmentCycle ?? null)

  function toggleSelection(applicationId: string) {
    setSelectedIds((previous) =>
      previous.includes(applicationId)
        ? previous.filter((id) => id !== applicationId)
        : [...previous, applicationId],
    )
  }

  // Gui ids dung thu tu tick, khong sap lai (R-O1).
  function openComparison() {
    navigate(`/hr/jobs/${jobId}/compare?ids=${selectedIds.join(',')}`)
  }

  return (
    <div className="flex flex-col gap-4 p-6">
      {job?.blindReviewEnabled && (
        <div className="flex items-start gap-2 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface-container px-4 py-3 text-sm text-m3-on-surface">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
          <p className="min-w-0 break-words">{BLIND_REVIEW_TEXT.applicationsTabNotice}</p>
        </div>
      )}
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
        <>
          <CompareSelectionBar
            selectedCount={selectedIds.length}
            onClear={() => setSelectedIds([])}
            onCompare={openComparison}
          />
          <div className="rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-12 text-center">{COMPARE_SELECTION_TEXT.columnHeader}</TableHead>
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
                    selection={{
                      checked: selectedIds.includes(application.id),
                      blockReason: selectionBlockReason(application, selectedIds, firstSelectedCycle),
                      onToggle: () => toggleSelection(application.id),
                    }}
                  />
                ))}
              </TableBody>
            </Table>
          </div>
        </>
      )}

      {createScoringRunMutation.isError && (
        <p className="text-sm text-m3-error">
          {extractErrorMessage(createScoringRunMutation.error, 'Tạo lượt chấm điểm thất bại, vui lòng thử lại.')}
        </p>
      )}
    </div>
  )
}

import type { ParseStatus } from '../resumes/types'
import type { ScoringRunStatus } from './types'

// Dieu kien disable nut "Cham diem ho so": CV chua parse xong, hoac don dang co luot
// cham chua hoan tat - khop dieu kien tien quyet cua backend
// (ScoringRunService.requireNoRunInProgress).
//
// Trang thai FAILED co y KHONG bi chan: backend set finished_at cung luc voi status
// trong ScoringRunStateService.markFailed, nen finishedAt khac null va runInProgress
// = false. Day la duong phuc hoi duy nhat cua HR khi mot luot cham that bai - chan
// nham o day se lam don ket vinh vien.
//
// Dung chung cho ApplicationsTab.tsx (D2) va CandidatesTable.tsx (F3); tham so go
// kieu bang structural type noi tuyen vi ca hai item type deu khop san.
export function scoringDisabledReason(application: {
  resumeParseStatus: ParseStatus
  latestScoringRunStatus: ScoringRunStatus | null
  latestScoringRunFinishedAt: string | null
}): string | undefined {
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

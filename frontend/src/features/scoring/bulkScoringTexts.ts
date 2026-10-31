import type { BulkScoringSkipReason, BulkScoringSkippedApplication } from './types'

// FR-H04 mo rong - chuoi UI.md muc 7. Chu trung tinh, khong ten hay id don (R-G3), khong nhan theo ket qua cham (R-G2).

// Khop ScoringRunService.MAX_BULK_SCORING_RUNS (R-C1) - chi dung de tinh k = min(N, 50) trong hop xac nhan (R-F3);
// backend van la chot tran.
export const BULK_SCORING_LIMIT = 50

export const BULK_SCORING_REASON_ID = 'bulk-scoring-reason'

// Q13, R-F5 - thu tu R-E3; cau bo qua duyet dung mang nay.
export const SKIP_REASON_ORDER: readonly BulkScoringSkipReason[] = [
  'NOT_PENDING',
  'RESUME_NOT_PARSED',
  'RUN_IN_PROGRESS',
  'ALREADY_SCORED',
]

export const SKIP_REASON_LABEL: Record<BulkScoringSkipReason, (count: number) => string> = {
  NOT_PENDING: (count) => `${count} không ở trạng thái chờ duyệt`,
  RESUME_NOT_PARSED: (count) => `${count} CV chưa trích xuất xong`,
  RUN_IN_PROGRESS: (count) => `${count} đang có lượt chấm chạy`,
  ALREADY_SCORED: (count) => `${count} đã có điểm`,
}

export const BULK_SCORING_TEXT = {
  button: (count: number) => `Chấm tất cả (${count})`,
  reasonNoneScorable:
    'Không có đơn nào cần chấm: chỉ chấm đơn đang chờ duyệt, CV đã trích xuất xong, chưa có điểm và không có lượt chấm đang chạy, thuộc chu kỳ tuyển dụng hiện tại.',
  reasonJobDeleted: 'Tin tuyển dụng đã bị xoá, không thể chấm điểm.',
  reasonJobLoading: 'Đang tải thông tin tin tuyển dụng.',
  dialogTitle: 'Chấm tất cả hồ sơ chưa có điểm?',
  applicationCount: (count: number) => `Số đơn sẽ xếp vào hàng chấm: ${count}`,
  overLimit: (rest: number) =>
    `Mỗi lần chỉ xếp tối đa ${BULK_SCORING_LIMIT} đơn nộp sớm nhất; còn ${rest} đơn, bấm lại sau để xếp tiếp.`,
  duration: (count: number) =>
    `Thời gian ước tính: khoảng ${count} phút — các đơn được chấm lần lượt, mỗi đơn khoảng 1 phút.`,
  cancel: 'Huỷ',
  confirm: 'Chấm tất cả',
  confirming: 'Đang xếp hàng...',
  resultCreated: (count: number) => `Đã xếp ${count} đơn vào hàng chấm.`,
  resultNoneCreated: 'Không có đơn nào được xếp vào hàng chấm.',
  resultRemaining: (count: number) =>
    `Còn ${count} đơn chưa xếp vì mỗi lần tối đa ${BULK_SCORING_LIMIT} đơn; bấm lại để xếp tiếp.`,
  networkError: 'Không xếp được các đơn vào hàng chấm, vui lòng thử lại.',
}

// Q13 - "Bo qua {k} don: ..." chi liet ke ly do co so > 0, dung thu tu R-E3, noi bang ", ". Dem tu skipped[].reason cua
// response, khong suy tu danh sach. Khong co don bi bo qua -> null (khong hien cau).
export function skippedSummary(skipped: readonly BulkScoringSkippedApplication[]): string | null {
  if (skipped.length === 0) {
    return null
  }
  const parts = SKIP_REASON_ORDER.map((reason) => ({
    reason,
    count: skipped.filter((row) => row.reason === reason).length,
  }))
    .filter(({ count }) => count > 0)
    .map(({ reason, count }) => SKIP_REASON_LABEL[reason](count))
  return `Bỏ qua ${skipped.length} đơn: ${parts.join(', ')}.`
}

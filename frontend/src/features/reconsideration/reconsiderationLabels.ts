import type { ReconsiderationStatus } from './types'

// FR-C09 UI.md muc 7 - chuoi co dinh phia ung vien (dot 5) va phia HR (dot 6).

export const RECONSIDERATION_MAX_LENGTH = 1000

// R-V1, R-V2 - cung cach backend dem: chi doi CRLF/CR -> LF roi lay String.length (khong trim). O nhap trong (hoac chi
// khoang trang) van gui duoc; backend luu NULL.
export function normalizeLineBreaks(text: string): string {
  return text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
}

export function reconsiderationTextLength(text: string): number {
  return normalizeLineBreaks(text).length
}

export const CANDIDATE_RECONSIDERATION_TEXT = {
  pendingBesideBadge: 'Đang chờ xem xét lại',
  sectionTitle: 'Xem xét lại',
  available: 'Bạn có thể đề nghị nhà tuyển dụng xem xét lại đơn này một lần.',
  deadline: (time: string) => `Hạn chót: ${time}.`,
  requestButton: 'Yêu cầu xem xét lại',
  expired: (time: string) => `Đã quá hạn gửi yêu cầu xem xét lại (hạn chót ${time}).`,
  jobRemoved: 'Tin tuyển dụng đã bị gỡ nên không gửi được yêu cầu xem xét lại.',
  open: (time: string) => `Đang chờ nhà tuyển dụng xem xét lại. Gửi lúc ${time}.`,
  accepted: (time: string) =>
    `Nhà tuyển dụng đã xem xét lại và gửi giấy mời phỏng vấn lúc ${time}. Xem giấy mời bên dưới.`,
  declined: (time: string) => `Nhà tuyển dụng đã xem xét lại và giữ quyết định từ chối lúc ${time}.`,
  hrResponseLabel: 'Phản hồi của nhà tuyển dụng',
  noHrResponse: 'Nhà tuyển dụng không gửi kèm phản hồi.',
  reasonLabel: 'Lý do bạn đã gửi',
  dialogTitle: 'Yêu cầu xem xét lại',
  dialogDescription: 'Nhà tuyển dụng sẽ nhận được yêu cầu và quyết định mời phỏng vấn hoặc giữ quyết định từ chối.',
  dialogNotice: (time: string) =>
    `Mỗi đơn chỉ gửi được một lần. Sau khi gửi, bạn không sửa hay huỷ được yêu cầu. Hạn chót: ${time}.`,
  reasonInputLabel: 'Lý do (không bắt buộc)',
  reasonHint: 'Nhà tuyển dụng sẽ đọc nguyên văn nội dung này.',
  cancel: 'Huỷ',
  submit: 'Gửi yêu cầu',
  submitting: 'Đang gửi...',
  tooLong: 'Nội dung tối đa 1000 ký tự.',
  submitError: 'Gửi yêu cầu thất bại, vui lòng thử lại.',
  submittedAnnouncement: 'Đã gửi yêu cầu xem xét lại.',
} as const

// Chip trang thai YEU CAU - mot kieu trung tinh cho ca ba (UI.md 5d, muc 10), luon la chu.
export const RECONSIDERATION_STATUS_LABELS: Record<ReconsiderationStatus, string> = {
  OPEN: 'Đang chờ',
  ACCEPTED: 'Đã mời phỏng vấn',
  DECLINED: 'Đã giữ từ chối',
}

export const HR_RECONSIDERATION_TEXT = {
  tabLabel: 'Yêu cầu xem xét lại',
  tabLabelWithCount: (count: number) => `Yêu cầu xem xét lại (${count})`,
  tabTitle: 'Yêu cầu xem xét lại',
  tabDescription: 'Ứng viên bị từ chối ở vòng hồ sơ có thể gửi một yêu cầu trong 7 ngày.',
  filterLabel: 'Lọc yêu cầu',
  filterOpen: 'Đang chờ',
  filterAll: 'Tất cả',
  emptyOpen: 'Không có yêu cầu xem xét lại nào đang chờ.',
  emptyAll: 'Tin này chưa có yêu cầu xem xét lại nào.',
  loadError: 'Không tải được yêu cầu xem xét lại, vui lòng thử lại.',
  retry: 'Thử lại',
  blockTitle: 'Yêu cầu xem xét lại',
  appliedOn: (date: string) => `Nộp đơn ${date}`,
  rejectedOn: (date: string) => `Bị từ chối ${date}`,
  requestedOn: (date: string) => `Gửi yêu cầu ${date}`,
  decidedOn: (date: string) => `Quyết định ${date}`,
  reasonLabel: 'Lý do',
  noReason: 'Ứng viên không ghi lý do.',
  hrResponseLabel: 'Phản hồi đã gửi ứng viên',
  noHrResponse: 'Không gửi kèm phản hồi.',
  openProfile: 'Mở hồ sơ',
  invite: 'Mời phỏng vấn',
  decline: 'Giữ quyết định từ chối',
  actionBarPending: 'Ứng viên đã gửi yêu cầu xem xét lại. Xử lý ở khối bên dưới.',
  declineDialogTitle: (name: string) => `Giữ quyết định từ chối — ${name}`,
  declineDialogDescription: 'Đơn vẫn ở trạng thái Bị từ chối. Ứng viên sẽ nhận thông báo về kết quả xem xét lại.',
  declineDialogNotice:
    'Phản hồi bên dưới được gửi cho ứng viên và hiện ở trang chi tiết đơn của họ. Đây không phải ghi chú nội bộ. Sau khi xác nhận không sửa được.',
  responseInputLabel: 'Phản hồi cho ứng viên (không bắt buộc)',
  cancel: 'Huỷ',
  confirmDecline: 'Giữ quyết định từ chối',
  submitting: 'Đang gửi...',
  tooLong: 'Nội dung tối đa 1000 ký tự.',
  declineError: 'Lưu quyết định thất bại, vui lòng thử lại.',
  declinedAnnouncement: 'Đã giữ quyết định từ chối.',
} as const

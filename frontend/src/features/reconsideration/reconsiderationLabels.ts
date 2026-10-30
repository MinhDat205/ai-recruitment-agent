// FR-C09 UI.md muc 7 - chuoi co dinh phia ung vien (dot 5). Phan HR them o dot 6.

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

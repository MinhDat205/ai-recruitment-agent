// FR-H12 UI.md muc 7 - chuoi co dinh cua giay moi nhieu khung gio (tieng Viet co dau, giu nguyen van UI.md).
// Gioi han so khung KHONG khai o day: luon lay maxSlots tu backend (preview/I1, R-S1).
export const INVITATION_TEXT = {
  tabTitle: 'Giấy mời phỏng vấn',
  inviteTitle: (candidateName: string) => `Mời phỏng vấn — ${candidateName}`,
  resendTitle: (candidateName: string) => `Gửi lại giấy mời — ${candidateName}`,
  slotsTitle: 'Khung giờ phỏng vấn',
  slotsHint: (maxSlots: number) =>
    `Thêm 1–${maxSlots} khung giờ. Nếu chỉ có một khung, khung đó được coi là lịch đã chốt. Giờ theo múi giờ của máy bạn.`,
  slotLegend: (n: number) => `Khung giờ ${n}`,
  slotTimeLabel: 'Thời điểm bắt đầu',
  slotLocationLabel: 'Địa điểm hoặc link',
  removeSlot: (n: number) => `Xoá khung giờ ${n}`,
  addSlot: 'Thêm khung giờ',
  addSlotLimit: (maxSlots: number) => `Đã đủ ${maxSlots} khung giờ.`,
  slotAdded: (n: number) => `Đã thêm khung giờ ${n}.`,
  slotRemoved: (n: number) => `Đã xoá khung giờ ${n}.`,
  resendNotice:
    'Giấy mời mới sẽ thay giấy mời hiện tại. Giấy mời hiện tại vẫn được lưu nguyên văn ở mục "Giấy mời trước đó". Trạng thái đơn không đổi. Ứng viên sẽ nhận thông báo và email kèm các khung giờ mới.',
  resendConfirmedNotice: (time: string) =>
    `Ứng viên đã chốt khung ${time}. Khung này sẽ không còn hiệu lực sau khi gửi lại.`,
  submitInvite: 'Gửi lời mời',
  submitResend: 'Gửi lại giấy mời',
  submitting: 'Đang gửi...',
  inviteFailed: 'Gửi lời mời thất bại, vui lòng thử lại.',
  resendFailed: 'Gửi lại giấy mời thất bại, vui lòng thử lại.',
  loadFailed: 'Không tải được giấy mời, vui lòng thử lại.',
  retry: 'Thử lại',
  activeTitle: 'Giấy mời hiện tại',
  sentAt: (time: string) => `Gửi lúc ${time}`,
  unconfirmed: 'Ứng viên chưa chọn khung giờ. Muốn chốt ngay một khung, hãy gửi lại giấy mời chỉ với khung đó.',
  resendButton: 'Gửi lại giấy mời',
  emptyPending: 'Chưa gửi giấy mời phỏng vấn cho đơn này. Dùng nút "Mời phỏng vấn" ở đầu trang để gửi.',
  emptyOther: 'Đơn này chưa từng được mời phỏng vấn.',
  // FR-U10 UI.md muc 7 (R-U2) - thay cau Q8 cua H12: huong dan chon, chi khi phan chon hien (4a). Nhay don de giu nguyen
  // dau ngoac kep lon trong chuoi (L27).
  candidateUnconfirmed:
    'Chọn một khung giờ phù hợp rồi bấm "Xác nhận khung giờ". Nếu không khung nào phù hợp, hãy nhắn cho nhà tuyển dụng qua tab Trao đổi.',
  // FR-U10 UI.md muc 7 - phia ung vien (4a, 4b-1, 4b-3) va hop xac nhan (4c).
  candidateAllPassed:
    'Các khung giờ trong giấy mời đã qua. Hãy nhắn cho nhà tuyển dụng qua tab Trao đổi để sắp xếp lịch khác.',
  candidateConfirmedAt: (time: string) =>
    `Bạn đã chọn khung giờ này lúc ${time}. Muốn đổi lịch, hãy nhắn cho nhà tuyển dụng qua tab Trao đổi.`,
  slotElapsed: 'Đã qua',
  locationPrefix: 'Địa điểm: ',
  confirmSlotButton: 'Xác nhận khung giờ',
  slotRequired: 'Vui lòng chọn một khung giờ.',
  confirmDialogTitle: 'Xác nhận khung giờ phỏng vấn',
  confirmDialogIntro: 'Bạn chọn khung giờ:',
  confirmDialogTime: (time: string) => `${time} (giờ Việt Nam)`,
  confirmDialogWarning:
    'Sau khi xác nhận, bạn không tự đổi được khung giờ. Muốn đổi lịch, bạn cần nhắn cho nhà tuyển dụng qua tab Trao đổi.',
  confirmDialogCancel: 'Huỷ',
  confirmDialogConfirm: 'Xác nhận',
  confirmDialogConfirming: 'Đang xác nhận...',
  confirmSlotFailed: 'Xác nhận khung giờ thất bại, vui lòng thử lại.',
  slotListTitle: 'Khung giờ (giờ Việt Nam)',
  missingLocation: 'Chưa có dữ liệu',
  confirmed: 'Đã chốt',
  previousToggle: (n: number) => `Giấy mời trước đó (${n})`,
  previousMeta: (sentAt: string, supersededAt: string) =>
    `Gửi lúc ${sentAt} · Đã được thay bằng giấy mời gửi lúc ${supersededAt}`,
} as const

// UI.md muc 7 - loi Zod cua tung khung trong hop thoai.
export const SLOT_FORM_ERRORS = {
  timeRequired: 'Vui lòng chọn thời điểm phỏng vấn',
  timePast: 'Thời điểm phỏng vấn phải ở trong tương lai',
  timeDuplicate: (m: number) => `Khung giờ này trùng thời điểm với khung giờ ${m}.`,
  locationRequired: 'Vui lòng nhập địa điểm hoặc link phỏng vấn',
  locationTooLong: 'Địa điểm tối đa 500 ký tự',
} as const

// Khop gioi han backend R-S6 (500 ky tu sau khi bo khoang trang hai dau).
export const SLOT_LOCATION_MAX = 500

const SLOT_TIME_PARTS = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Ho_Chi_Minh',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

// UI.md muc 7 - gio khung "HH:mm dd/MM/yyyy" theo GIO VIET NAM o moi noi hien khung (HR, ung vien, email R-N3) de
// hai ben va email thay cung mot con so. Ghep tu formatToParts de khong phu thuoc thu tu ngay/gio cua locale.
export function formatSlotTime(iso: string): string {
  const parts: Record<string, string> = {}
  for (const part of SLOT_TIME_PARTS.formatToParts(new Date(iso))) {
    parts[part.type] = part.value
  }
  return `${parts.hour}:${parts.minute} ${parts.day}/${parts.month}/${parts.year}`
}

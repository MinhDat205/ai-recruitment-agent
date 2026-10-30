// FR-U16 UI.md muc 7 - chuoi hien thi (tieng Viet co dau). Cau 409 lay nguyen van tu backend, khong lap lai o day.
export const SAVED_JOB_TEXT = {
  saveLabeled: 'Lưu tin',
  savedLabeled: 'Đã lưu',
  unsaveThisJobAria: 'Bỏ lưu tin này',
  saveErrorFallback: 'Không lưu được tin, vui lòng thử lại.',
  unsaveErrorFallback: 'Không bỏ lưu được tin, vui lòng thử lại.',
  idsLoadError: 'Không tải được trạng thái lưu, vui lòng tải lại trang.',
  // Trang "Tin da luu" (UI.md muc 7).
  pageTitle: 'Tin đã lưu',
  apply: 'Ứng tuyển',
  unsave: 'Bỏ lưu',
  unsaving: 'Đang bỏ lưu...',
  applied: 'Đã ứng tuyển',
  viewApplication: 'Xem đơn →',
  empty: 'Bạn chưa lưu tin nào.',
  findJobs: 'Tìm việc làm',
  listLoadError: 'Không tải được danh sách tin đã lưu.',
  retry: 'Thử lại',
  missingData: 'Chưa có dữ liệu',
  salaryUnlisted: 'Không công bố',
  confirmTitle: 'Bỏ lưu tin này?',
  cancel: 'Huỷ',
} as const

// R-V4 - cau ly do khi nut Ung tuyen bi khoa (tin khong OPEN, chua co don).
export const UNAVAILABLE_REASONS = {
  EXPIRED: 'Tin đã hết hạn nộp hồ sơ.',
  PAUSED: 'Nhà tuyển dụng đang tạm dừng nhận hồ sơ.',
  CLOSED: 'Tin đã đóng, không nhận thêm hồ sơ.',
  UNAVAILABLE: 'Tin không còn được đăng.',
} as const

export function savedCountText(count: number, max: number): string {
  return `Đang lưu ${count}/${max} tin`
}

export function savedAtText(formatted: string): string {
  return `Đã lưu ${formatted}`
}

export function confirmUnsaveText(title: string): string {
  return `Tin "${title}" không còn nhận hồ sơ. Sau khi bỏ lưu, bạn sẽ không lưu lại được tin này.`
}

export function saveJobAria(title: string): string {
  return `Lưu tin ${title}`
}

export function unsaveJobAria(title: string): string {
  return `Bỏ lưu tin ${title}`
}

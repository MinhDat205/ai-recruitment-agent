import type { JobAvailability } from './types'

// FR-U08 UI.md muc 7 - nhan tinh trang tin, dung 5 gia tri (R-D4). UNAVAILABLE gop tin da xoa va tin dua ve
// nhap: ung vien khong can biet HR da xoa tin hay dua ve nhap.
export const JOB_AVAILABILITY_LABELS: Record<JobAvailability, string> = {
  OPEN: 'Đang mở',
  EXPIRED: 'Đã hết hạn nộp',
  PAUSED: 'Tạm dừng',
  CLOSED: 'Đã đóng',
  UNAVAILABLE: 'Không còn đăng',
}

// FR-U08 R-D4 - tinh trang tin, dung 5 gia tri, backend tinh (frontend KHONG tu suy tu deadline). Tin da xoa mem va
// tin dua ve nhap gop chung UNAVAILABLE: ung vien khong can biet HR da xoa tin hay dua ve nhap. FR-U16 R-A5 - chuyen
// nguyen tu candidateApplicationDetail/ (types.ts + jobAvailabilityLabels.ts) de trang "Tin da luu" dung chung.
export type JobAvailability = 'OPEN' | 'EXPIRED' | 'PAUSED' | 'CLOSED' | 'UNAVAILABLE'

// FR-U08 UI.md muc 7 - nhan nguyen van.
export const JOB_AVAILABILITY_LABELS: Record<JobAvailability, string> = {
  OPEN: 'Đang mở',
  EXPIRED: 'Đã hết hạn nộp',
  PAUSED: 'Tạm dừng',
  CLOSED: 'Đã đóng',
  UNAVAILABLE: 'Không còn đăng',
}

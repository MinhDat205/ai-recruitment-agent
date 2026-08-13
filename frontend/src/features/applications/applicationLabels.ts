import type { ApplicationStatus } from './types'

export const APPLICATION_STATUS_LABELS: Record<ApplicationStatus, string> = {
  PENDING: 'Chờ duyệt',
  INTERVIEW_INVITED: 'Đã mời phỏng vấn',
  HIRED: 'Trúng tuyển',
  REJECTED: 'Bị từ chối',
  WITHDRAWN: 'Đã rút đơn',
}

// Chi dung token da khai o @theme trong index.css (khong hardcode hex). Day la badge trang
// thai, khong phai phan quyet dung-sai - KHONG dung --color-success/-danger/-warning (xem
// UI_GUIDE.md muc badge trang thai don ung tuyen).
export const APPLICATION_STATUS_STYLES: Record<ApplicationStatus, string> = {
  PENDING: 'bg-status-pending text-status-pending-text',
  INTERVIEW_INVITED: 'bg-status-invited text-status-invited-text',
  HIRED: 'bg-status-hired text-status-hired-text',
  REJECTED: 'bg-status-rejected text-status-rejected-text',
  WITHDRAWN: 'bg-status-withdrawn text-status-withdrawn-text',
}

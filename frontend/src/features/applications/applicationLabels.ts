import type { ApplicationStatus } from './types'

export const APPLICATION_STATUS_LABELS: Record<ApplicationStatus, string> = {
  PENDING: 'Chờ duyệt',
  INTERVIEW_INVITED: 'Đã mời phỏng vấn',
  HIRED: 'Trúng tuyển',
  REJECTED: 'Bị từ chối',
  WITHDRAWN: 'Đã rút đơn',
}

// Chi dung token m3-* da khai o @theme trong index.css (khong hardcode hex). Day la badge trang
// thai, khong phai phan quyet dung-sai - KHONG dung cap xanh la/do goi y tot-xau (UI_GUIDE muc 3,
// bang da chot 05/10/2026 o ROADMAP Phase 2.1b). Cac trang thai phan biet bang muc nhan manh
// (nen dac / nen nhat / chi vien), luon kem chu.
export const APPLICATION_STATUS_STYLES: Record<ApplicationStatus, string> = {
  PENDING: 'bg-m3-surface-container text-m3-on-surface',
  INTERVIEW_INVITED: 'bg-m3-primary-container text-m3-on-primary-container',
  HIRED: 'bg-m3-primary text-m3-on-primary',
  REJECTED: 'border border-m3-outline bg-m3-surface text-m3-on-surface',
  WITHDRAWN: 'border border-m3-outline-variant bg-m3-surface text-m3-on-surface-variant',
}

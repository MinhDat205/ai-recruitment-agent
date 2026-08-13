import type { JobStatus } from './ownerTypes'

export const JOB_STATUS_LABELS: Record<JobStatus, string> = {
  DRAFT: 'Nháp',
  OPEN: 'Đang mở',
  PAUSED: 'Tạm dừng',
  CLOSED: 'Đã đóng',
}

export const JOB_STATUS_OPTIONS: JobStatus[] = ['DRAFT', 'OPEN', 'PAUSED', 'CLOSED']

// Backend (JobOwnerService.changeStatus) chap nhan MOI chuyen trang thai - day chi la goi y
// hop ly ve nghiep vu de an nut, KHONG PHAI co che chan. An nut khong phai bao mat: quyen so
// huu da duoc JobOwnerService kiem o tang API. Danh sach nay chi tranh HR bam nham thao tac
// vo nghia (vd "tam dung" mot job con dang DRAFT chua tung mo).
export const NEXT_STATUS_ACTIONS: Record<JobStatus, { status: JobStatus; label: string }[]> = {
  DRAFT: [{ status: 'OPEN', label: 'Mở tin' }],
  OPEN: [
    { status: 'PAUSED', label: 'Tạm dừng' },
    { status: 'CLOSED', label: 'Đóng tin' },
  ],
  PAUSED: [
    { status: 'OPEN', label: 'Mở lại' },
    { status: 'CLOSED', label: 'Đóng tin' },
  ],
  CLOSED: [{ status: 'OPEN', label: 'Mở lại' }],
}

export const EMPLOYMENT_TYPE_LABELS: Record<string, string> = {
  FULL_TIME: 'Toàn thời gian',
  PART_TIME: 'Bán thời gian',
  CONTRACT: 'Hợp đồng',
  INTERNSHIP: 'Thực tập',
  FREELANCE: 'Freelance',
}

export const EMPLOYMENT_TYPE_OPTIONS = Object.keys(EMPLOYMENT_TYPE_LABELS)

export const WORK_MODE_LABELS: Record<string, string> = {
  ONSITE: 'Tại văn phòng',
  HYBRID: 'Kết hợp',
  REMOTE: 'Từ xa',
}

export const WORK_MODE_OPTIONS = Object.keys(WORK_MODE_LABELS)

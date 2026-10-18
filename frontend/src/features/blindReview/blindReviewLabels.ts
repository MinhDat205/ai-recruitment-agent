import { isAxiosError } from 'axios'

// FR-H16 - chuoi giao dien (UI.md muc 7). Nhan "Ung vien #XXXXXXXX" va chuoi da che deu den tu API - frontend KHONG dung
// nhan hay tu che (UI.md muc 10). Cau loi ma backend hien nguyen van tu body (extractErrorMessage); cac cau o day chi la
// cau du phong khi khong co body (mat mang...).
export const BLIND_REVIEW_TEXT = {
  cardTitle: 'Chế độ ẩn danh',
  stateOff: 'Đang tắt.',
  stateOn: 'Đang bật.',
  description:
    'Khi bật: hồ sơ hiện dạng “Ứng viên #…”, không tải được CV gốc, thư giới thiệu và thông tin liên hệ được ẩn. Hồ sơ tự hiện danh tính khi bạn gửi giấy mời phỏng vấn.',
  enable: 'Bật ẩn danh',
  disable: 'Tắt ẩn danh',
  cancel: 'Huỷ',
  saving: 'Đang lưu…',
  confirmEnableTitle: 'Bật chế độ ẩn danh cho tin này?',
  confirmEnableMessage: 'Áp dụng ngay cho mọi hồ sơ chưa được mời phỏng vấn. Thông báo đã nhận trước đó không thay đổi.',
  confirmDisableTitle: 'Tắt chế độ ẩn danh cho tin này?',
  confirmDisableMessage: 'Họ tên và thông tin liên hệ của mọi hồ sơ sẽ hiện lại. Việc bật/tắt được ghi lại.',
  toggleFailed: 'Không lưu được chế độ ẩn danh, vui lòng thử lại.',
  applicationsTabNotice: 'Tin này đang bật chế độ ẩn danh. Hồ sơ chưa được mời phỏng vấn hiện dạng “Ứng viên #…”.',
  badge: 'Ẩn danh',
  downloadBlocked: 'Không tải được trong chế độ ẩn danh.',
  saveToPoolBlocked:
    'Chỉ lưu vào kho sau khi hồ sơ hiện danh tính (gửi giấy mời phỏng vấn hoặc tắt chế độ ẩn danh).',
  submittedResumeNotice: 'Chế độ ẩn danh: không tải được CV gốc, tên tệp và thư giới thiệu được ẩn.',
  contactMasked: 'Thông tin liên hệ được ẩn trong chế độ ẩn danh.',
  hiddenFileName: 'Tệp đính kèm (đã ẩn tên)',
  invitationNotice:
    'Họ tên ứng viên sẽ được điền vào chỗ {{candidateName}} khi gửi. Hồ sơ hiện danh tính sau khi gửi giấy mời.',
} as const

// UI.md 4f - mot dieu kien duy nhat cho ca ba diem mo hop thoai giay moi: preview con nguyen chu placeholder (backend
// giu nguyen khi don dang an danh - R-I1).
export const CANDIDATE_NAME_PLACEHOLDER = '{{candidateName}}'

// UI.md muc 6 - tai CV goc / tep ung vien gui ma tra 409 (du lieu doi giua chung: Job vua bat an danh). Loi tai dang Blob
// nen khong doc duoc cau backend (gioi han da biet, xem downloadApplicationResume.ts); 409 o hai endpoint tai nay CHI la
// IDENTITY_MASKED (R-V1, R-T2) nen hien cau ly do khoa. Ma khac -> cau du phong cua noi goi.
export function downloadErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err) && err.response?.status === 409) {
    return BLIND_REVIEW_TEXT.downloadBlocked
  }
  return fallback
}

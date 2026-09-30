import type { Resume } from './types'

// Chuoi hien thi trich xuat lai CV (FR-C05 UI.md muc 7) - mot cho cho danh sach CV.
export const REPARSE_TEXT = {
  legacyNote: 'Dữ liệu trích xuất theo phiên bản cũ.',
  button: 'Cập nhật dữ liệu trích xuất',
  buttonRunning: 'Đang cập nhật…',
  buttonRetry: 'Thử cập nhật lại',
  progressLabel: 'Đang cập nhật dữ liệu trích xuất',
  success: 'Đã cập nhật dữ liệu trích xuất.',
  requestFallbackError: 'Không gửi được yêu cầu cập nhật dữ liệu trích xuất, vui lòng thử lại.',
} as const

export function reparseFailureText(errorMessage: string | null): string {
  return errorMessage
    ? `Cập nhật dữ liệu trích xuất thất bại: ${errorMessage}. Dữ liệu cũ vẫn được giữ.`
    : 'Cập nhật dữ liệu trích xuất thất bại. Dữ liệu cũ vẫn được giữ.'
}

// R-R2 phia giao dien: nut chi hien voi CV da xu ly xong va du lieu con o schema v1. Backend van la chot
// chan (409) - day chi quyet dinh co hien nut hay khong.
export function canReparse(resume: Resume): boolean {
  return resume.parseStatus === 'DONE' && resume.schemaVersion === 1
}

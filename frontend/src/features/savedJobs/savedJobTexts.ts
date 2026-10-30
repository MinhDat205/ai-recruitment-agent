// FR-U16 UI.md muc 7 - chuoi hien thi (tieng Viet co dau). Cau 409 lay nguyen van tu backend, khong lap lai o day.
export const SAVED_JOB_TEXT = {
  saveLabeled: 'Lưu tin',
  savedLabeled: 'Đã lưu',
  unsaveThisJobAria: 'Bỏ lưu tin này',
  saveErrorFallback: 'Không lưu được tin, vui lòng thử lại.',
  unsaveErrorFallback: 'Không bỏ lưu được tin, vui lòng thử lại.',
  idsLoadError: 'Không tải được trạng thái lưu, vui lòng tải lại trang.',
} as const

export function saveJobAria(title: string): string {
  return `Lưu tin ${title}`
}

export function unsaveJobAria(title: string): string {
  return `Bỏ lưu tin ${title}`
}

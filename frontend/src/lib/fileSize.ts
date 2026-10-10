// FR-C06 R-C3 - MOT ham dinh dang dung luong tep dung chung (chuyen nguyen tu ResumeList.tsx). Dung o danh
// sach CV (FR-U01) va the tep dinh kem tin nhan (FR-C06). Khong co ban thu hai.
export function formatFileSize(bytes: number | null): string {
  if (bytes === null) {
    return ''
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(0)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

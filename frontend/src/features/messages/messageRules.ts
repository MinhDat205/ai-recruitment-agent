// FR-C06 - hang so kiem o trinh duyet, KHOP backend (MessageService): R-M3 noi dung toi da 4000 ky tu; R-F3 tep
// toi da 5MB. Backend van la noi quyet dinh cuoi cung - trinh duyet chi chan som de giu chu dang nhap.
export const MAX_BODY_LENGTH = 4000

export const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024

// Duoi tep chap nhan o trinh duyet (theo ten). Backend nhan dang loai CHI bang magic bytes (R-F2) - kiem duoi o
// day chi de bao loi som, khong thay the kiem o backend.
export const ALLOWED_ATTACHMENT_EXTENSIONS = ['pdf', 'docx', 'png', 'jpg', 'jpeg', 'webp'] as const

// Gia tri cho thuoc tinh accept cua <input type="file">.
export const ATTACHMENT_ACCEPT = ALLOWED_ATTACHMENT_EXTENSIONS.map((extension) => `.${extension}`).join(',')

// UI.md muc 7 - trung nguyen van cau backend.
export const ATTACHMENT_ERRORS = {
  invalidType: 'Định dạng không hợp lệ, chỉ nhận PDF, DOCX, PNG, JPEG hoặc WEBP',
  tooLarge: 'Tệp đính kèm vượt quá 5MB',
  empty: 'Tệp đính kèm đang trống',
} as const

// Tra cau loi (UI.md muc 7) hoac null khi tep hop le o phia trinh duyet. Thu tu kiem khop backend (REQUIREMENT
// muc 12 L4): 0 byte -> qua 5MB -> sai loai.
export function validateAttachment(file: File): string | null {
  if (file.size === 0) {
    return ATTACHMENT_ERRORS.empty
  }
  if (file.size > MAX_ATTACHMENT_BYTES) {
    return ATTACHMENT_ERRORS.tooLarge
  }
  const dot = file.name.lastIndexOf('.')
  const extension = dot >= 0 ? file.name.slice(dot + 1).toLowerCase() : ''
  if (!(ALLOWED_ATTACHMENT_EXTENSIONS as readonly string[]).includes(extension)) {
    return ATTACHMENT_ERRORS.invalidType
  }
  return null
}

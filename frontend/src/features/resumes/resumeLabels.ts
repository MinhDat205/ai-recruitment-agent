import type { ParseStatus } from './types'

export const PARSE_STATUS_LABELS: Record<ParseStatus, string> = {
  PENDING: 'Chờ xử lý',
  PROCESSING: 'Đang xử lý',
  DONE: 'Hoàn tất',
  FAILED: 'Lỗi',
}

// Chi dung token da khai o @theme trong index.css (khong hardcode hex). FAILED la trang thai loi
// xu ly he thong (khong doc duoc file / parse that bai), khong phai phan quyet ung vien nen
// dung --color-danger o day khong vi pham UI_GUIDE muc 5.
export const PARSE_STATUS_STYLES: Record<ParseStatus, string> = {
  PENDING: 'bg-canvas text-ink-muted',
  PROCESSING: 'bg-canvas text-ink-muted',
  DONE: 'bg-brand-light text-brand',
  FAILED: 'bg-danger/10 text-danger',
}

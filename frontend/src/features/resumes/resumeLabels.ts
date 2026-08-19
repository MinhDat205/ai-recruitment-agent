import type { ParseStatus } from './types'

export const PARSE_STATUS_LABELS: Record<ParseStatus, string> = {
  PENDING: 'Chờ xử lý',
  PROCESSING: 'Đang xử lý',
  DONE: 'Đã xử lý xong',
  FAILED: 'Không đọc được',
}

// Chi dung token da khai o @theme trong index.css (khong hardcode hex). FAILED o day nghia la "he
// thong khong doc duoc file" (loi ky thuat), KHONG phai "CV kem" - vi vay dung mau TRUNG TINH
// (giong PENDING/PROCESSING), khong dung --color-danger de tranh gay an tuong day la phan quyet ve
// chat luong CV. DONE dung --color-brand mang y nghia thong tin (da xu ly xong), khong phai "tot".
export const PARSE_STATUS_STYLES: Record<ParseStatus, string> = {
  PENDING: 'bg-canvas text-ink-muted',
  PROCESSING: 'bg-canvas text-ink-muted',
  DONE: 'bg-brand-light text-brand',
  FAILED: 'bg-canvas text-ink-muted',
}

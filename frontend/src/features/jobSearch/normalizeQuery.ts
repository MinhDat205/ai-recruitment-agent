import { JOB_SEARCH_MAX_LENGTH, JOB_SEARCH_MIN_LENGTH } from './jobSearchLabels'

// Dung tap khoang trang cua \s trong Java [ \t\n\x0B\f\r] (L7; \v = U+000B). KHONG dung \s cua JS: no khop ca NBSP,
// U+FEFF, U+2028, U+3000 ma backend khong gop -> bo dem lech backend.
const JAVA_WHITESPACE_RUN = /[ \t\n\v\f\r]+/g

// Nhu String.trim() cua Java: bo moi ky tu <= U+0020 o hai dau (khong dung trim() cua JS - L7).
function javaTrim(text: string): string {
  let start = 0
  let end = text.length
  while (start < end && text.charCodeAt(start) <= 0x20) {
    start++
  }
  while (end > start && text.charCodeAt(end - 1) <= 0x20) {
    end--
  }
  return text.slice(start, end)
}

// R-I2 phia client - CHI de dem do dai giong backend (NFC -> gop khoang trang -> trim). Du lieu gui di van la chuoi ung
// vien go, backend tu chuan hoa lai.
export function normalizeQuery(raw: string): string {
  return javaTrim(raw.normalize('NFC').replace(JAVA_WHITESPACE_RUN, ' '))
}

export type QueryValidationKey = 'nlEmpty' | 'nlTooShort' | 'nlTooLong'

// R-I3 - cung thu tu va cung cau voi backend; null = hop le. Ky tu khong ho tro chi kiem o backend (UI.md muc 7).
export function validateQuery(raw: string): QueryValidationKey | null {
  const length = normalizeQuery(raw).length
  if (length === 0) {
    return 'nlEmpty'
  }
  if (length < JOB_SEARCH_MIN_LENGTH) {
    return 'nlTooShort'
  }
  if (length > JOB_SEARCH_MAX_LENGTH) {
    return 'nlTooLong'
  }
  return null
}

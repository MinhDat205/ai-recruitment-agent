// FR-U13 - moi chuoi hien thi cua che do tim bang mo ta, nguyen van UI.md muc 7. Cau loi kiem dau vao trung nguyen van
// REQUIREMENT muc 4.4 (backend). Mot cho duy nhat.

// R-I3 - cung so voi backend (JobSearchService).
export const JOB_SEARCH_MIN_LENGTH = 2
export const JOB_SEARCH_MAX_LENGTH = 300
// UI.md muc 5a - chia trang o trinh duyet, 10 viec/trang.
export const JOB_SEARCH_PAGE_SIZE = 10

export const JOB_SEARCH_TEXT = {
  modeLegend: 'Cách tìm việc',
  modeKeyword: 'Tìm theo từ khoá',
  modeKeywordShort: 'Từ khoá',
  modeDescription: 'Tìm bằng mô tả',
  modeDescriptionShort: 'Mô tả',
  nlInputLabel: 'Mô tả công việc bạn muốn tìm',
  nlPlaceholder: 'Mô tả công việc bạn muốn tìm…',
  nlHint: 'Ví dụ: content marketing lương trên 15 triệu ở Hà Nội, không yêu cầu kinh nghiệm.',
  nlQuotaNote: 'Mỗi lần tìm dùng một lượt AI.',
  nlIntro:
    'Mô tả bằng lời công việc bạn muốn. Hệ thống tách điều kiện lọc (khu vực, ngành nghề, lương, hình thức làm việc) và xếp việc làm theo phần mô tả còn lại.',
  nlSubmit: 'Tìm',
  nlSubmitting: 'Đang tìm…',
  nlProgress: 'Đang phân tích mô tả và tìm việc làm',
  nlEmpty: 'Vui lòng nhập mô tả công việc bạn muốn tìm.',
  nlTooShort: 'Mô tả cần ít nhất 2 ký tự.',
  nlTooLong: 'Mô tả tìm việc tối đa 300 ký tự.',
  aiBadge: 'Do AI tạo',
  interpretationTitle: 'Hệ thống hiểu yêu cầu của bạn',
  nlNoDescription: 'Không còn phần mô tả để xếp theo độ liên quan; việc làm được sắp theo thời gian đăng mới nhất.',
  nlNoConditions: 'Không tách được điều kiện lọc nào từ mô tả.',
  nlPersonalInfoIgnored:
    'Đã bỏ qua thông tin nhân thân trong mô tả. Hệ thống không lọc hay xếp việc làm theo các thông tin này.',
  interpretationCaution: 'Kiểm tra lại các điều kiện; bấm vào một điều kiện để sửa bằng bộ lọc.',
  editConditions: 'Sửa điều kiện bằng bộ lọc',
  resultsTitle: 'Kết quả tìm theo mô tả',
  matchedPrefix: 'Khớp điều kiện:',
  nlUnrankedDivider:
    'Các việc làm dưới đây chưa được hệ thống phân tích nội dung nên chưa xếp theo mô tả; sắp theo thời gian đăng mới nhất.',
  nlEmptyResults: 'Không tìm thấy việc làm nào khớp các điều kiện đã hiểu.',
  nlFailedFallback: 'Chưa tìm được việc làm theo mô tả, vui lòng thử lại.',
  retry: 'Thử lại',
  switchedNotice: 'Đã chuyển sang lọc thường, bỏ thứ tự theo mô tả.',
} as const

export function nlCounter(length: number): string {
  return `${length}/${JOB_SEARCH_MAX_LENGTH}`
}

export function descriptionLine(description: string): string {
  return `Xếp theo mô tả: “${description}”`
}

export function chipEditAria(conditionText: string): string {
  return `Sửa điều kiện: ${conditionText}`
}

export function chipRemoveAria(conditionText: string): string {
  return `Xoá điều kiện: ${conditionText}`
}

export function nlResultCount(count: number): string {
  return `Tìm thấy ${count} việc làm (tối đa 50)`
}

// Nhan the luong (trieu VND): salaryFrom / salaryTo / salaryRange. Khong co moc nao -> null (khong co the luong).
export function salaryConditionLabel(min: number | null, max: number | null): string | null {
  if (min != null && max != null) {
    return `Lương ${min}–${max} triệu`
  }
  if (min != null) {
    return `Lương từ ${min} triệu`
  }
  if (max != null) {
    return `Lương đến ${max} triệu`
  }
  return null
}

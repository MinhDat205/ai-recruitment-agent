import { MAX_COMPARE, MIN_COMPARE } from './compareLimits'
import type { ComparisonEducationItem, ComparisonExperience } from './types'

// FR-H14 UI.md muc 7 - moi chuoi cua tab "Ung vien" (phan chon don) va trang so sanh, dung nguyen van. Cau sang loc
// dung lai SCREENING_ANSWER_TEXT/SCREENING_QUESTION_TYPE_LABELS (features/screening/screeningLabels.ts), khong chep o day.

export const COMPARE_SELECTION_TEXT = {
  columnHeader: 'Chọn',
  checkboxLabel: (candidateName: string) => `Chọn đơn của ${candidateName} để so sánh`,
  selectedCount: (n: number) => `Đã chọn ${n}/${MAX_COMPARE}`,
  clear: 'Bỏ chọn',
  compare: 'So sánh',
  needMore: `Chọn ít nhất ${MIN_COMPARE} đơn để so sánh.`,
  limitReached: `Đã chọn đủ ${MAX_COMPARE} đơn.`,
  // Ly do khoa o chon (UI.md muc 7, theo thu tu uu tien o ApplicationsTab).
  notScored: 'Chưa có lượt chấm hoàn tất nên chưa so sánh được.',
  differentCycle: 'Khác chu kỳ tuyển dụng với đơn đã chọn.',
} as const

export const COMPARE_PAGE_TEXT = {
  pageTitle: 'So sánh ứng viên',
  backToList: 'Quay lại danh sách ứng viên',
  subtitle: (jobTitle: string, cycle: number, count: number) =>
    `${jobTitle} · Chu kỳ tuyển dụng ${cycle} · ${count} đơn`,
  decisionNote: 'Bảng chỉ hiển thị dữ liệu đã có của từng đơn. Quyết định thuộc về bạn, trên từng hồ sơ.',
  reload: 'Tải lại',
  tableCaption: (count: number, jobTitle: string) => `So sánh ${count} ứng viên của tin ${jobTitle}`,
  rubricVersionsNotice:
    'Các đơn được chấm theo phiên bản rubric khác nhau. Hàng tiêu chí có lưu ý bên dưới được hiển thị theo đúng phiên bản của từng đơn, không quy đổi.',
} as const

export const COMPARE_COLUMN_TEXT = {
  appliedAt: (dateTime: string) => `Nộp ${dateTime}`,
  scoredAt: (dateTime: string) => `Chấm lúc ${dateTime}`,
  newerRunInProgress: (dateTime: string) =>
    `Đang có lượt chấm mới; bảng đang hiện kết quả lượt hoàn tất lúc ${dateTime}.`,
  openProfile: 'Mở hồ sơ',
  openProfileAria: (candidateName: string) => `Mở hồ sơ của ${candidateName}`,
  invite: 'Mời phỏng vấn',
  inviteAria: (candidateName: string) => `Mời ${candidateName} phỏng vấn`,
  ordinal: (index: number, candidateName: string) => `${index} · ${candidateName}`,
} as const

export const COMPARE_GROUP_TEXT = {
  summary: 'Điểm tổng hợp',
  criteria: 'Điểm theo tiêu chí',
  profile: 'Hồ sơ từ CV',
  profileNote: 'Dữ liệu trích xuất hiện tại của CV đã nộp',
  explanation: 'Tóm tắt điểm mạnh, điểm yếu',
  aiGenerated: 'Do AI tạo',
  screening: 'Câu trả lời sàng lọc',
  screeningNote: 'Chỉ để tham khảo, không dùng để chấm điểm hay xếp hạng.',
} as const

export const COMPARE_ROW_TEXT = {
  totalScore: 'Tổng điểm',
  rank: 'Hạng',
  rankNote: 'Hạng trong toàn bộ đơn của tin này (FR-H05), không phải hạng trong nhóm đang so sánh.',
  experience: 'Số năm kinh nghiệm',
  skills: 'Kỹ năng',
  education: 'Học vấn',
  summary: 'Tóm tắt',
  // Giu nguyen tieu de cua ExplanationReport.tsx:95-96.
  strengths: 'Điểm mạnh',
  weaknesses: 'Điểm cần cải thiện',
  diff: 'Khác nhau',
} as const

export const COMPARE_CRITERION_TEXT = {
  score: (score: string, maxScore: number) => `${score}/${maxScore} điểm`,
  weight: (weight: string) => `Trọng số ${weight}%`,
  expand: 'Lý giải và trích dẫn',
  // Giu nguyen cau cua FR-H06 (CriterionScoreBreakdown.tsx).
  aiReasoningNote: 'Diễn giải của AI cho tiêu chí này — không phải khuyến nghị tuyển dụng',
  source: (section: string) => `Nguồn: ${section}`,
  noEvidence: 'Không tìm thấy trích dẫn nào trong CV cho tiêu chí này.',
  nameInRubric: (name: string) => `Tên trong rubric của đơn: ${name}`,
  missingCriterion: 'Đơn này được chấm theo phiên bản rubric không có tiêu chí này.',
  rowVersionsDiffer: 'Các đơn được chấm theo phiên bản rubric khác nhau ở tiêu chí này.',
} as const

// Giu nguyen cau cua ExplanationReport.tsx (:14, :19, :77-78, :86-87).
export const COMPARE_EXPLANATION_TEXT = {
  note: 'Tổng hợp của AI dựa trên các tiêu chí đã chấm — không phải khuyến nghị tuyển dụng, HR tự xem xét từng hồ sơ.',
  pending: 'Báo cáo tổng hợp của AI đang được xử lý.',
  failed: 'Không tạo được báo cáo tổng hợp của AI cho lượt chấm này.',
  emptyList: 'Không có mục nào.',
  point: (criterionName: string, point: string) => `${criterionName} — ${point}`,
} as const

// Cau MOI cua H14 (R-Q2/R-Q4); cac cau sang loc con lai dung SCREENING_ANSWER_TEXT.
export const COMPARE_SCREENING_TEXT = {
  notAsked: 'Không có câu hỏi này lúc ứng viên nộp đơn.',
  statusRow: 'Trạng thái',
} as const

export const MISSING_DATA = 'Chưa có dữ liệu'

export const COMPARE_ERROR_TEXT = {
  loadFailed: 'Không tải được dữ liệu so sánh, vui lòng thử lại.',
  retry: 'Thử lại',
  notFound: 'Không tìm thấy tin tuyển dụng.',
  backToJobs: 'Về danh sách tin tuyển dụng',
  notScoredHint: 'Bạn có thể chấm điểm hồ sơ ở tab Ứng viên rồi so sánh lại.',
  // Cung chu voi lien ket tao cong ty o AiDraftInputPanel (JOB_DRAFT_TEXT.createCompany).
  createCompany: 'Tạo hồ sơ công ty',
} as const

// "{years} nam ({months} thang)" - years do backend quy doi (ExperienceCalculator.toYears), hien dau thap phan kieu
// Viet (4,5). Ca hai null -> "Chua co du lieu" (R-D5).
export function formatExperience(experience: ComparisonExperience): string {
  if (experience.months === null || experience.years === null) {
    return MISSING_DATA
  }
  const years = experience.years.toLocaleString('vi-VN', { maximumFractionDigits: 1 })
  return `${years} năm (${experience.months} tháng)`
}

// Moi muc hoc van mot dong: "bang cap · chuyen nganh · truong · bat dau–ket thuc", chi ghep truong khac rong, chuoi
// nguyen van CV (R-D7, R-D11). Khong co diem trung binh [Q10].
export function formatEducation(item: ComparisonEducationItem): string {
  const period = [item.startDate, item.endDate].filter((part): part is string => Boolean(part?.trim())).join('–')
  return [item.degree, item.major, item.school, period]
    .filter((part): part is string => Boolean(part?.trim()))
    .join(' · ')
}

import type { FieldNeed, JobDraftField, JobDraftPart } from './types'

// FR-H11 UI.md muc 7 - chuoi co dinh, dung nguyen van, khai MOT cho.

export const DESCRIPTION_MAX = 3000
export const MAX_KEPT_CRITERIA = 10
export const MAX_KEPT_QUESTIONS = 10
export const CRITERION_NAME_MAX = 200

export const PART_LABELS: Record<JobDraftPart, string> = {
  JOB: 'Bản nháp tin',
  CRITERIA: 'Tiêu chí đánh giá',
  QUESTIONS: 'Câu hỏi sàng lọc',
}

export const FIELD_LABELS: Record<JobDraftField, string> = {
  TITLE: 'Tiêu đề',
  DESCRIPTION: 'Mô tả công việc',
  CATEGORY: 'Ngành nghề',
  LOCATION: 'Tỉnh/thành',
  EMPLOYMENT_TYPE: 'Loại hình làm việc',
  WORK_MODE: 'Hình thức làm việc',
  SALARY: 'Mức lương',
  DEADLINE: 'Hạn ứng tuyển',
  REQUIREMENTS: 'Yêu cầu ứng viên',
  BENEFITS: 'Quyền lợi',
}

// Truong cua bieu mau tao tin ma "Dien vao bieu mau" ghi vao (buoc 1-2). Nhan dung nhu nhan o cua bieu mau - dung cho
// hop thoai ghi de (4f): "Ban da sua 2 truong: Tieu de, Luong toi thieu".
export type FillableField =
  | 'title'
  | 'description'
  | 'requirements'
  | 'benefits'
  | 'categoryCode'
  | 'locationCode'
  | 'employmentType'
  | 'workMode'
  | 'salaryMin'
  | 'salaryMax'
  | 'salaryCurrency'
  | 'deadline'

export const FILLABLE_FIELD_LABELS: Record<FillableField, string> = {
  title: 'Tiêu đề',
  description: 'Mô tả công việc',
  requirements: 'Yêu cầu ứng viên',
  benefits: 'Quyền lợi',
  categoryCode: 'Ngành nghề',
  locationCode: 'Tỉnh/thành',
  employmentType: 'Loại hình làm việc',
  workMode: 'Hình thức làm việc',
  salaryMin: 'Lương tối thiểu',
  salaryMax: 'Lương tối đa',
  salaryCurrency: 'Đơn vị tiền tệ',
  deadline: 'Hạn ứng tuyển',
}

export const NEED_ORDER: FieldNeed[] = ['SAVE', 'OPEN', 'OPTIONAL']

export const NEED_LABELS: Record<FieldNeed, string> = {
  SAVE: 'Bắt buộc để lưu nháp:',
  OPEN: 'Bắt buộc để mở tin:',
  OPTIONAL: 'Nên có:',
}

export const AI_STEP_TITLES = [
  'Thông tin cơ bản',
  'Lương & thời hạn',
  'Tiêu chí đánh giá',
  'Câu hỏi sàng lọc',
  'Mẫu giấy mời phỏng vấn',
] as const

export const JOB_DRAFT_TEXT = {
  panelTitle: 'Tạo tin bằng AI',
  openButton: 'Tạo tin bằng AI',
  intro:
    'Mô tả vị trí bằng lời thường. AI gợi ý bản nháp tin, tiêu chí đánh giá và câu hỏi sàng lọc; bạn xem và quyết định từng phần trước khi lưu.',
  descriptionLabel: 'Mô tả vị trí cần tuyển',
  descriptionPlaceholder:
    'Ví dụ: Cần tuyển 2 lập trình viên Java backend tại Đà Nẵng, toàn thời gian, lương 15 - 20 triệu…',
  descriptionHelper:
    'Nêu những gì bạn biết: vị trí, công việc, yêu cầu, mức lương, địa điểm, hình thức làm việc, quyền lợi, hạn nộp. Thông tin không nêu sẽ được để trống. Không nhập thông tin cá nhân của bất kỳ ai.',
  counter: (n: number) => `${n}/${DESCRIPTION_MAX}`,
  quotaNote: 'Mỗi lần tạo dùng 1 lượt gọi AI.',
  generate: 'Tạo bản nháp',
  closeLabel: 'Đóng tạo tin bằng AI',
  running: 'Đang tạo…',
  done: 'Đã có gợi ý',
  progressLabel: (part: string) => `Đang tạo ${part}`,
  staleNotice: 'Chưa tạo lại được phần này; đang hiện gợi ý trước đó.',
  summary: (text: string) => `Mô tả của bạn: "${text}…"`,
  editDescription: 'Sửa mô tả',
  regenerateAll: 'Tạo lại tất cả',
  aiBlockLabel: 'Do AI tạo',
  previewTitle: 'Bản nháp tin',
  previewRows: {
    title: 'Tiêu đề',
    category: 'Ngành nghề',
    location: 'Tỉnh/thành',
    employmentType: 'Loại hình',
    workMode: 'Hình thức',
    salary: 'Lương',
    deadline: 'Hạn ứng tuyển',
    description: 'Mô tả',
    requirements: 'Yêu cầu',
    benefits: 'Quyền lợi',
  },
  noData: 'Chưa có dữ liệu',
  showMore: 'Xem đầy đủ',
  showLess: 'Thu gọn',
  fill: 'Điền vào biểu mẫu',
  filled: 'Đã điền bản nháp tin vào biểu mẫu. Hãy đọc lại từng trường trước khi lưu.',
  aiSuggested: 'Do AI gợi ý',
  benefitsLabel: 'Quyền lợi',
  benefitsHelper: 'Khi lưu, quyền lợi được đặt ở cuối phần mô tả công việc, sau dòng "Quyền lợi:".',
  missingTitle: 'Cần bổ sung',
  missingFixedLine: 'Luôn do bạn nhập: trọng số tiêu chí, mẫu giấy mời phỏng vấn.',
  compactStep: (n: number, total: number) => `Bước ${n}/${total}`,
  criteriaIntro: 'Trọng số do bạn nhập. Có thể lưu nháp khi tổng chưa đủ 100%; cần đủ 100% để mở tin.',
  criteriaSuggestions: 'Gợi ý tiêu chí',
  keep: 'Giữ',
  discard: 'Bỏ',
  keepAll: 'Giữ tất cả',
  regenerate: 'Tạo lại',
  keptCriteria: (n: number) => `Tiêu chí đã giữ (${n})`,
  weightLabel: 'Trọng số (%)',
  edit: 'Sửa',
  doneEditing: 'Xong',
  cancel: 'Huỷ',
  totalWeight: (n: string) => `Tổng trọng số: ${n}/100`,
  weightRequired: 'Vui lòng nhập trọng số.',
  weightRange: 'Trọng số phải lớn hơn 0 và tối đa 100.',
  weightTotalExceeded: 'Tổng trọng số vượt quá 100%.',
  criterionNameRequired: 'Vui lòng nhập tên tiêu chí.',
  criterionNameTooLong: 'Tên tiêu chí tối đa 200 ký tự.',
  criterionNameDuplicate: 'Tên tiêu chí bị trùng.',
  criterionNameLabel: 'Tên tiêu chí',
  criterionDescriptionLabel: 'Mô tả tiêu chí',
  criteriaEmpty: 'AI chưa gợi ý được tiêu chí nào từ mô tả này. Bạn có thể thêm tiêu chí ở trang sửa tin sau khi lưu.',
  criteriaLimit: 'Đã giữ đủ 10 tiêu chí.',
  questionSuggestions: 'Gợi ý câu hỏi',
  keptQuestions: (n: number) => `Câu hỏi đã giữ (${n}/${MAX_KEPT_QUESTIONS})`,
  requiredAnswer: 'Bắt buộc trả lời',
  questionsEmpty: 'AI chưa gợi ý được câu hỏi nào từ mô tả này. Câu hỏi sàng lọc là tuỳ chọn.',
  questionsLimit: 'Đã giữ đủ 10 câu hỏi (tối đa của một tin).',
  keepCriterionLabel: (name: string) => `Giữ tiêu chí ${name}`,
  discardCriterionLabel: (name: string) => `Bỏ tiêu chí ${name}`,
  keepQuestionLabel: (text: string) => `Giữ câu hỏi ${text}`,
  discardQuestionLabel: (text: string) => `Bỏ câu hỏi ${text}`,
  templateIntro: 'Mẫu giấy mời do bạn tự điền; AI không soạn phần này.',
  beforeSave:
    'Tin sẽ được lưu ở trạng thái Nháp. Bạn mở tin ở trang sửa tin khi đã đủ 100% trọng số và đủ ngành nghề, tỉnh/thành.',
  save: 'Lưu nháp',
  saving: 'Đang lưu…',
  overwriteTitle: 'Ghi đè các trường bạn đã sửa?',
  overwriteBody: (n: number, fields: string) =>
    `Bạn đã sửa ${n} trường: ${fields}. Bản nháp mới sẽ điền vào các trường còn lại.`,
  overwriteKeep: 'Chỉ điền trường chưa sửa',
  overwriteAll: 'Ghi đè tất cả',
  retry: 'Thử lại',
  generateFallbackError: 'Chưa tạo được gợi ý, vui lòng thử lại.',
  saveFallbackError: 'Lưu nháp thất bại, vui lòng thử lại.',
  createCompany: 'Tạo hồ sơ công ty',
} as const

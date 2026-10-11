import type { ScreeningQuestionType } from './types'

// FR-H10 UI.md muc 7 - chuoi co dinh, dung nguyen van.

export const SCREENING_QUESTION_TYPES: ScreeningQuestionType[] = ['SHORT_TEXT', 'SINGLE_CHOICE', 'YES_NO']

export const SCREENING_QUESTION_TYPE_LABELS: Record<ScreeningQuestionType, string> = {
  SHORT_TEXT: 'Trả lời ngắn',
  SINGLE_CHOICE: 'Một lựa chọn',
  YES_NO: 'Có/Không',
}

// Gioi han kiem som o frontend (REQUIREMENT R-Q3, R-Q4); backend van la chot chan.
export const QUESTION_TEXT_MAX = 300
export const OPTION_TEXT_MAX = 100
export const OPTIONS_MIN = 2
export const OPTIONS_MAX = 10

export const SCREENING_TEXT = {
  tabTitle: 'Câu hỏi sàng lọc',
  counter: (n: number, max: number) => `${n}/${max} câu hỏi`,
  noticeLines: [
    'Không hỏi thông tin nhân thân như tuổi, giới tính, tình trạng hôn nhân, tôn giáo, sức khoẻ, dân tộc, ảnh.',
    'Câu trả lời chỉ để bạn tham khảo: hệ thống không tự loại ứng viên, không dùng câu trả lời để chấm điểm hay xếp hạng.',
    'Thay đổi chỉ áp dụng cho đơn nộp sau; đơn đã nộp giữ nguyên câu hỏi và câu trả lời lúc nộp.',
  ],
  empty: 'Tin này chưa có câu hỏi sàng lọc. Ứng viên nộp đơn sẽ không phải trả lời câu nào.',
  addFirst: 'Thêm câu hỏi đầu tiên',
  add: 'Thêm câu hỏi',
  limitReached: (max: number) => `Đã đủ ${max} câu hỏi. Xoá bớt một câu để thêm câu mới.`,
  required: 'Bắt buộc',
  notRequired: 'Không bắt buộc',
  moveUp: (n: number) => `Đưa câu ${n} lên`,
  moveDown: (n: number) => `Đưa câu ${n} xuống`,
  edit: (n: number) => `Sửa câu ${n}`,
  remove: (n: number) => `Xoá câu ${n}`,
  moved: (text: string, position: number) => `Đã chuyển câu "${text}" tới vị trí ${position}.`,
  deleteTitle: 'Xoá câu hỏi?',
  deleteBody:
    'Câu hỏi sẽ không còn xuất hiện với ứng viên nộp đơn sau. Đơn đã nộp vẫn giữ nguyên câu hỏi và câu trả lời.',
  cancel: 'Huỷ',
  deleteConfirm: 'Xoá',
  deleting: 'Đang xoá...',
  dialogTitleAdd: 'Thêm câu hỏi',
  dialogTitleEdit: 'Sửa câu hỏi',
  dialogDescription:
    'Câu trả lời chỉ để bạn tham khảo, không dùng để chấm điểm hay xếp hạng. Không hỏi thông tin nhân thân.',
  fieldText: 'Nội dung câu hỏi',
  charCounter: (n: number) => `${n}/${QUESTION_TEXT_MAX} ký tự`,
  fieldType: 'Loại câu hỏi',
  fieldOptions: 'Các phương án (2–10)',
  addOption: 'Thêm phương án',
  removeOption: (n: number) => `Xoá phương án ${n}`,
  fieldRequired: 'Bắt buộc trả lời',
  save: 'Lưu câu hỏi',
  saving: 'Đang lưu...',
  retry: 'Thử lại',
  loadError: 'Không tải được câu hỏi sàng lọc.',
  saveError: 'Lưu câu hỏi thất bại, vui lòng thử lại.',
  deleteError: 'Xoá câu hỏi thất bại, vui lòng thử lại.',
  reorderError: 'Đổi thứ tự thất bại, vui lòng thử lại.',
} as const

// Loi frontend (Zod) - UI.md muc 7.
export const SCREENING_FORM_ERRORS = {
  textEmpty: 'Vui lòng nhập nội dung câu hỏi',
  textTooLong: 'Tối đa 300 ký tự',
  optionsTooFew: 'Cần ít nhất 2 phương án',
  optionsTooMany: 'Tối đa 10 phương án',
  optionEmpty: 'Phương án không được để trống',
  optionTooLong: 'Mỗi phương án tối đa 100 ký tự',
  optionDuplicate: 'Các phương án không được trùng nhau',
} as const

// Dem ky tu theo code point SAU khi bo khoang trang hai dau - khop backend (strip + codePointCount, R-Q4b).
export function countChars(value: string): number {
  return [...value.trim()].length
}

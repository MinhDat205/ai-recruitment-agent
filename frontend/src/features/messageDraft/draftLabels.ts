import type { DraftScenario, DraftSide, DraftTone, DraftUnavailableReason } from './types'

// FR-C07 - moi chuoi hien thi cua khoi soan nhap, nguyen van UI.md muc 7. Mot cho duy nhat.

export const DRAFT_SCENARIO_LABELS: Record<DraftScenario, string> = {
  REQUEST_MORE_INFO: 'Đề nghị bổ sung thông tin',
  INTERVIEW_REMINDER: 'Nhắc lịch phỏng vấn',
  THANK_FOR_APPLYING: 'Cảm ơn đã ứng tuyển',
  RESULT_NOTICE: 'Thông báo kết quả',
  ASK_PROGRESS: 'Hỏi tiến độ',
  THANK_AFTER_INTERVIEW: 'Cảm ơn sau phỏng vấn',
  REQUEST_RESCHEDULE: 'Xin dời lịch phỏng vấn',
  CUSTOM: 'Tự mô tả',
}

export const DRAFT_TONE_LABELS: Record<DraftTone, string> = {
  FORMAL: 'Trang trọng',
  FRIENDLY: 'Thân thiện',
}

export const DRAFT_UNAVAILABLE_REASON_LABELS: Record<DraftUnavailableReason, string> = {
  RESULT_NOT_FINAL: 'Chỉ dùng được khi đơn đã có kết quả Trúng tuyển hoặc Bị từ chối.',
  NO_ACTIVE_INTERVIEW: 'Chỉ dùng được khi đơn đang chờ phỏng vấn và đã có giấy mời.',
  NO_INTERVIEW: 'Chỉ dùng được khi đơn đã có giấy mời phỏng vấn.',
}

export const CUSTOM_PURPOSE_PLACEHOLDERS: Record<DraftSide, string> = {
  hr: 'Ví dụ: hỏi ứng viên có thể bắt đầu làm việc từ khi nào',
  candidate: 'Ví dụ: hỏi công ty có cần bổ sung bằng cấp không',
}

// R-S4
export const MAX_CUSTOM_PURPOSE_LENGTH = 500

export const DRAFT_TEXT = {
  openButton: 'Soạn bằng AI',
  panelTitle: 'Soạn bằng AI',
  closeButtonLabel: 'Đóng soạn bằng AI',
  scenarioLegend: 'Tình huống',
  customPurposeLabel: 'Mô tả mục đích tin nhắn',
  toneLabel: 'Giọng văn',
  generate: 'Tạo bản nháp',
  drafting: 'Đang soạn bản nháp…',
  progressLabel: 'Đang soạn bản nháp',
  aiBadge: 'Do AI tạo',
  change: 'Đổi',
  useDraft: 'Dùng bản nháp',
  regenerate: 'Tạo lại',
  dismiss: 'Bỏ qua',
  retry: 'Thử lại',
  usedHint: 'Bản nháp do AI soạn. Hãy đọc lại, thay các chỗ trong [ngoặc vuông] và sửa nếu cần trước khi gửi.',
  replaceTitle: 'Thay nội dung đang soạn?',
  replaceDescription: 'Ô nhập đang có nội dung. Bản nháp sẽ thay thế toàn bộ nội dung này.',
  keepCurrent: 'Giữ nội dung đang soạn',
  replaceWithDraft: 'Thay bằng bản nháp',
  scenariosLoadFailed: 'Không tải được danh sách tình huống.',
  conversationNotFound: 'Không tìm thấy cuộc trao đổi.',
  draftFailedFallback: 'Chưa soạn được bản nháp, vui lòng thử lại.',
} as const

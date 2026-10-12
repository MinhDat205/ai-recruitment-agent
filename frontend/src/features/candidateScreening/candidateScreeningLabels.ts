// FR-U09 UI.md muc 7 - chuoi phia ung vien, dung nguyen van. Nhan loai cau hoi va "Bat buoc"/"Khong bat buoc" dung lai
// SCREENING_QUESTION_TYPE_LABELS, SCREENING_TEXT cua features/screening/screeningLabels.ts (chi import, khong sua).

// Gioi han kiem som o frontend (R-V1); backend van la chot chan.
export const ANSWER_TEXT_MAX = 500

export const CANDIDATE_SCREENING_TEXT = {
  blockTitle: 'Câu hỏi sàng lọc',
  notice:
    'Câu trả lời giúp nhà tuyển dụng có thêm thông tin tham khảo. Hệ thống không dùng câu trả lời để chấm điểm hay xếp hạng hồ sơ.',
  charCounter: (n: number) => `${n}/${ANSWER_TEXT_MAX} ký tự`,
  yes: 'Có',
  no: 'Không',
  clear: 'Bỏ chọn',
  clearAria: (position: number) => `Bỏ chọn câu trả lời của câu ${position}`,
  loadError: 'Không tải được câu hỏi sàng lọc của tin này.',
  retry: 'Thử lại',
  submitBlocked: 'Cần tải được câu hỏi sàng lọc trước khi nộp đơn.',
  jobNotAccepting: 'Tin tuyển dụng này hiện không nhận đơn.',
  // Cau 409 cua backend (REQUIREMENT muc 4.6) - chi la fallback khi response khong co message.
  questionsChanged: 'Nhà tuyển dụng vừa cập nhật câu hỏi sàng lọc. Vui lòng kiểm tra lại câu trả lời rồi nộp lại.',
  talentPoolLabel: 'Cho phép công ty lưu hồ sơ của tôi để liên hệ cho các vị trí sau này.',
  talentPoolHelp:
    'Không bắt buộc và không ảnh hưởng tới việc xét tuyển đơn này. Bạn có thể rút đồng ý bất cứ lúc nào ở trang chi tiết đơn ứng tuyển.',
  viewApplication: 'Xem đơn vừa nộp →',
} as const

// Loi Zod (UI.md muc 7). optionNotFound dung lai nguyen van cau R-V1 cua backend (L19).
export const CANDIDATE_SCREENING_ERRORS = {
  required: 'Vui lòng trả lời câu hỏi bắt buộc này.',
  tooLong: `Tối đa ${ANSWER_TEXT_MAX} ký tự.`,
  optionNotFound: 'Phương án đã chọn không có trong câu hỏi sàng lọc.',
} as const

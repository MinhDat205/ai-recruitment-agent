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

// Trang chi tiet don - khoi "Cau tra loi sang loc" (UI.md 4e, 4f, muc 7). date: chuoi da dinh dang formatDateTimeVi.
export const CANDIDATE_ANSWERS_TEXT = {
  title: 'Câu trả lời sàng lọc',
  caption: (date: string) =>
    `Câu hỏi và câu trả lời được lưu nguyên văn lúc bạn nộp đơn (${date}). Nhà tuyển dụng chỉ dùng để tham khảo, không dùng để chấm điểm hay xếp hạng.`,
  yes: 'Có',
  no: 'Không',
  noAnswer: 'Bạn không trả lời câu này.',
  loadError: 'Không tải được câu trả lời sàng lọc, vui lòng thử lại.',
  retry: 'Thử lại',
} as const

// Trang chi tiet don - khoi "Luu ho so cho dot tuyen sau" va hop xac nhan rut dong y (UI.md 4e, 4f, 4g, muc 7).
export const TALENT_POOL_TEXT = {
  title: 'Lưu hồ sơ cho đợt tuyển sau',
  active: (companyName: string, date: string) =>
    `Bạn đã đồng ý cho ${companyName} lưu hồ sơ để liên hệ cho các vị trí sau này (${date}).`,
  activeNote:
    'Rút đồng ý không ảnh hưởng tới trạng thái hay việc xét tuyển của đơn này. Sau khi rút, bạn không thể đồng ý lại cho đơn này.',
  notGiven: 'Bạn không đồng ý cho công ty lưu hồ sơ khi nộp đơn này.',
  revoked: (date: string) =>
    `Bạn đã rút đồng ý lưu hồ sơ (${date}). Công ty không được lưu hồ sơ từ đơn này cho các vị trí sau.`,
  revokeButton: 'Rút đồng ý',
  dialogTitle: 'Rút đồng ý lưu hồ sơ?',
  dialogBodySuffix:
    ' sẽ không được lưu hồ sơ của bạn từ đơn ứng tuyển này để liên hệ cho các vị trí sau. Bạn không thể đồng ý lại cho đơn này. Trạng thái và việc xét tuyển của đơn không thay đổi.',
  cancel: 'Huỷ',
  confirm: 'Xác nhận rút đồng ý',
  confirming: 'Đang rút đồng ý...',
  revokeError: 'Rút đồng ý thất bại, vui lòng thử lại.',
  revokedAnnouncement: 'Đã rút đồng ý lưu hồ sơ.',
} as const

// Loi Zod (UI.md muc 7). optionNotFound dung lai nguyen van cau R-V1 cua backend (L19).
export const CANDIDATE_SCREENING_ERRORS = {
  required: 'Vui lòng trả lời câu hỏi bắt buộc này.',
  tooLong: `Tối đa ${ANSWER_TEXT_MAX} ký tự.`,
  optionNotFound: 'Phương án đã chọn không có trong câu hỏi sàng lọc.',
} as const

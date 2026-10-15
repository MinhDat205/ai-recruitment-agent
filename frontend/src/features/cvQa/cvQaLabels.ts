import type { CvQaRefusalReason } from './types'

// FR-C08 - moi chuoi hien thi cua khoi hoi dap CV, nguyen van UI.md muc 7. Mot cho duy nhat.

// R-H1 - cung nguong voi backend.
export const MAX_QUESTION_LENGTH = 500

export const CV_QA_REFUSAL_LABELS: Record<CvQaRefusalReason, string> = {
  // Chi phia HR (S3) - backend khong tra ma nay cho ung vien.
  PERSONAL_INFO:
    'Hệ thống không trả lời câu hỏi về thông tin nhân thân (tuổi, giới tính, tình trạng hôn nhân, tôn giáo, sức khoẻ…).',
  HIRING_JUDGEMENT:
    'Hệ thống không đưa ra nhận định đạt hay không đạt, phù hợp hay không phù hợp. Hãy hỏi về một nội dung cụ thể trong CV, ví dụ một kinh nghiệm hay kỹ năng.',
  CV_IMPROVEMENT: 'Gợi ý bổ sung, cải thiện CV có ở chức năng Gợi ý cải thiện CV.',
  OUT_OF_SCOPE: 'Câu hỏi này không liên quan tới nội dung CV. Hãy hỏi về thông tin có trong CV.',
}

export const CV_QA_TEXT = {
  tabLabel: 'Hỏi đáp CV',
  pageTitle: 'Hỏi đáp CV',
  backToProfile: 'Về Hồ sơ và CV',
  resumeButton: 'Hỏi đáp CV',
  introHr:
    'Đặt câu hỏi về nội dung CV ứng viên đã nộp cho đơn này. AI chỉ trả lời dựa trên CV và kèm trích dẫn nguyên văn để bạn đối chiếu.',
  introCandidate: 'Đặt câu hỏi về nội dung CV này. AI chỉ trả lời dựa trên CV và kèm trích dẫn nguyên văn.',
  notSavedNotice: 'Các câu hỏi và câu trả lời không được lưu. Tải lại hoặc rời trang sẽ mất toàn bộ.',
  questionLabel: 'Câu hỏi của bạn',
  placeholderHr: 'Ví dụ: Ứng viên đã dùng Docker trong dự án nào?',
  placeholderCandidate: 'Ví dụ: CV của tôi ghi những chứng chỉ nào?',
  askButton: 'Hỏi',
  asking: 'Đang hỏi…',
  progressLabel: 'Đang trả lời câu hỏi',
  progressHint: 'AI đang đọc CV…',
  emptyTurns: 'Chưa có câu hỏi nào trong phiên này.',
  turnQuestionLabel: 'Câu hỏi',
  aiBadge: 'Do AI tạo',
  citationNote: 'Trích dẫn đã được đối chiếu khớp nguyên văn với CV. Phần trả lời là lời của AI — hãy dựa vào trích dẫn.',
  citationsHeading: 'Trích dẫn từ CV',
  notFound: 'Không tìm thấy căn cứ trong CV cho câu hỏi này.',
  truncatedNotice: 'CV dài, hệ thống chỉ dùng phần đầu và phần cuối của CV để trả lời.',
  refusedLinkCvImprovement: 'Mở Gợi ý cải thiện CV',
  emptyQuestion: 'Vui lòng nhập câu hỏi.',
  questionTooLong: 'Câu hỏi tối đa 500 ký tự.',
  askFailedFallback: 'Chưa trả lời được câu hỏi, vui lòng thử lại.',
  retry: 'Thử lại',
  unavailablePendingHr: 'CV của đơn này chưa trích xuất xong nên chưa hỏi đáp được.',
  unavailableFailedHr: 'Không trích xuất được nội dung CV của đơn này nên không hỏi đáp được.',
  unavailablePendingCandidate: 'CV này chưa trích xuất xong nên chưa hỏi đáp được.',
  unavailableFailedCandidate: 'Không trích xuất được nội dung CV này nên không hỏi đáp được.',
  resumeNotFound: 'Không tìm thấy CV này.',
} as const

// Khoa counter cua UI.md muc 7: "{n}/500".
export function cvQaCounter(n: number): string {
  return `${n}/${MAX_QUESTION_LENGTH}`
}

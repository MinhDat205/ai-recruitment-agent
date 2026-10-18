// FR-U12 - moi chuoi hien thi cua cong cu tao CV, nguyen van UI.md muc 7 (gom 4 chuoi cua L12) + bang nhan o va mau
// cau loi nguyen van REQUIREMENT muc 4.4 + gioi han R-F5. Mot cho duy nhat - frontend va backend cung cau.

// R-F5, R-F6, R-F9, R-A2 - cung so voi CvBuilderFormValidator / CvBuilderService.
export const CV_BUILDER_LIMITS = {
  fullName: 100,
  email: 254,
  phone: 30,
  address: 200,
  linkedin: 200,
  currentTitle: 150,
  locationText: 100,
  educationItems: 10,
  school: 200,
  degree: 100,
  major: 150,
  experienceItems: 15,
  company: 200,
  experienceTitle: 150,
  description: 2000,
  skillsItems: 50,
  tag: 50,
  certificationsItems: 20,
  certificationName: 200,
  issuer: 200,
  projectsItems: 15,
  projectName: 200,
  technologiesItems: 20,
  versionLabel: 100,
  minYear: 1950,
  maxYear: 2100,
  gpaMax: 10,
  gpaMaxScale: 2,
  rephraseMin: 20,
  rephraseMax: 800,
} as const

// UI.md muc 5a - placeholder cua MonthField.
export const CV_BUILDER_DATE_PLACEHOLDER = 'MM/YYYY'

// R-F5 - ma nganh "Ngành khác" co trong danh muc nhung backend tu choi (ResumeParsingService.EXCLUDED_INDUSTRY_CODE).
export const EXCLUDED_INDUSTRY_CODE = 'OTHER'

// Bang nhan o (REQUIREMENT 4.4, CvBuilderField.displayName) - nhan cua o tren form VA phan "{Ô}" cua cau loi.
export const CV_BUILDER_FIELD_LABELS = {
  fullName: 'Họ và tên',
  email: 'Email',
  phone: 'Điện thoại',
  address: 'Địa chỉ',
  linkedin: 'LinkedIn',
  currentTitle: 'Chức danh hiện tại',
  locationText: 'Khu vực',
  industryCode: 'Ngành nghề',
  school: 'Trường',
  degree: 'Bằng cấp',
  major: 'Chuyên ngành',
  startDate: 'Thời gian bắt đầu',
  endDate: 'Thời gian kết thúc',
  gpa: 'GPA',
  company: 'Tên công ty',
  experienceTitle: 'Chức danh',
  description: 'Mô tả',
  certificationName: 'Tên chứng chỉ',
  issuer: 'Đơn vị cấp',
  issueDate: 'Thời gian cấp',
  projectName: 'Tên dự án',
  technologies: 'Công nghệ',
  versionLabel: 'Tên phiên bản',
} as const

// Ten khoi (CvBuilderField EDUCATION/EXPERIENCE/SKILLS/CERTIFICATIONS/PROJECTS) - phan "{Khối}" cua cau loi va legend
// "{Khối} {i}" cua moi muc.
export const CV_BUILDER_BLOCK_LABELS = {
  education: 'Học vấn',
  experience: 'Kinh nghiệm',
  skills: 'Kỹ năng',
  certifications: 'Chứng chỉ',
  projects: 'Dự án',
} as const

export type CvBuilderBlock = keyof typeof CV_BUILDER_BLOCK_LABELS

// Mau cau loi nguyen van REQUIREMENT 4.4 (INVALID_CV_BUILDER_FORM, CV_BUILDER_EMPTY). {Ô}/{Mục}/{Khối} la phan ghep tu
// nhan o + so thu tu (cvBuilderSchema), {n} la so - KHONG bao gio chua gia tri nguoi dung da go (R-F11).
// unsupportedCharacter, wordTooLong: chi backend kiem (can font - UI.md muc 5a), frontend chi hien cau backend tra ve.
export const CV_BUILDER_ERROR_TEMPLATES = {
  required: '{Ô} là bắt buộc.',
  tooLong: '{Ô} tối đa {n} ký tự.',
  tooManyItems: '{Khối} tối đa {n} mục.',
  unsupportedCharacter: '{Ô} có ký tự không hỗ trợ trong CV.',
  wordTooLong: '{Ô}: có một từ quá dài, không vừa một dòng của CV. Hãy thêm dấu cách.',
  invalidDate: '{Ô} phải có dạng MM/YYYY hoặc YYYY, năm từ 1950 đến 2100.',
  startAfterEnd: '{Mục}: thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc.',
  currentWithEndDate: '{Mục}: đang làm thì không nhập thời gian kết thúc.',
  invalidGpa: '{Mục}: GPA phải từ 0 đến 10, tối đa 2 chữ số thập phân.',
  invalidEmail: 'Email không đúng định dạng.',
  unknownIndustry: 'Ngành nghề không có trong danh mục.',
  templateRequired: 'Vui lòng chọn mẫu trình bày.',
  empty: 'CV cần có họ và tên và ít nhất một mục ở học vấn, kinh nghiệm, kỹ năng, chứng chỉ hoặc dự án.',
} as const

export const CV_BUILDER_TEXT = {
  createButton: 'Tạo CV',
  editButton: 'Sửa CV',
  emptyResumeList: 'Chưa có CV nào. Tải lên CV đầu tiên hoặc bấm "Tạo CV" để tạo bằng công cụ.',
  backToProfile: 'Về Hồ sơ và CV',
  pageTitleCreate: 'Tạo CV',
  pageTitleEdit: 'Sửa CV',
  intro: 'Điền thông tin theo từng mục. Hệ thống tạo file PDF từ đúng nội dung bạn nhập.',
  editNotice: 'Lưu sẽ tạo một CV mới. CV này và các đơn đã nộp bằng nó giữ nguyên.',
  prefillTitle: 'Điền sẵn từ CV đã trích xuất (không bắt buộc)',
  prefillSelectPlaceholder: 'Chọn CV',
  prefillButton: 'Điền sẵn',
  prefillRunning: 'Đang điền…',
  // UI.md muc 6 "Dang tai danh sach CV (the Dien san)".
  prefillLoading: 'Đang tải danh sách CV…',
  prefillNone: 'Bạn chưa có CV nào đã xử lý xong để điền sẵn.',
  prefillConfirm: 'Thay toàn bộ nội dung đang nhập bằng dữ liệu từ CV đã chọn?',
  prefillConfirmOk: 'Thay nội dung',
  prefillConfirmCancel: 'Giữ nội dung hiện tại',
  prefillDone: 'Đã điền sẵn từ CV đã chọn. Hãy kiểm tra lại từng mục.',
  prefillDateNotice: 'Một số mốc thời gian trong CV đã chọn không có dạng MM/YYYY hoặc YYYY nên chưa được điền.',
  prefillFailed: 'Không tải được dữ liệu của CV đã chọn, vui lòng thử lại.',
  templateTitle: 'Mẫu trình bày',
  templateClassic: 'Cổ điển — Tên căn trái, tiêu đề mục có đường kẻ',
  templateModern: 'Hiện đại — Tên căn giữa, tiêu đề mục màu xanh đậm',
  sectionContact: 'Thông tin liên hệ',
  sectionGeneral: 'Thông tin chung',
  sectionExperience: 'Kinh nghiệm làm việc',
  sectionEducation: 'Học vấn',
  sectionProjects: 'Dự án',
  sectionSkills: 'Kỹ năng',
  sectionCertifications: 'Chứng chỉ',
  sectionVersion: 'Tên phiên bản',
  industryHint: 'Dùng để gợi ý việc làm, không in lên CV.',
  // UI.md muc 4a ("Chọn ngành ▾") - Select can cau placeholder va mot lua chon bo chon (o khong bat buoc).
  industryPlaceholder: 'Chọn ngành',
  industryNone: 'Không chọn ngành',
  locationHint: 'Ví dụ: Hà Nội, TP. Hồ Chí Minh.',
  dateHint: 'MM/YYYY hoặc YYYY',
  currentLabel: 'Đang làm ở đây',
  versionPlaceholder: 'Ví dụ: Bản CSKH 2026',
  addExperience: 'Thêm kinh nghiệm',
  addEducation: 'Thêm học vấn',
  addProject: 'Thêm dự án',
  addCertification: 'Thêm chứng chỉ',
  emptyExperience: 'Chưa có kinh nghiệm nào.',
  emptyEducation: 'Chưa có học vấn nào.',
  emptyProjects: 'Chưa có dự án nào.',
  emptyCertifications: 'Chưa có chứng chỉ nào.',
  emptySkills: 'Chưa có kỹ năng nào.',
  skillsPlaceholder: 'Nhập kỹ năng rồi nhấn Enter',
  technologiesPlaceholder: 'Nhập công nghệ rồi nhấn Enter',
  skillsMaxReached: 'Đã đạt tối đa 50 kỹ năng.',
  technologiesMaxReached: 'Đã đạt tối đa 20 công nghệ.',
  technologiesInputLabel: 'Nhập công nghệ mới',
  rephraseButton: 'Gợi ý diễn đạt',
  rephraseProgress: 'Đang tạo gợi ý diễn đạt',
  rephraseHint: 'AI đang diễn đạt lại đoạn mô tả…',
  aiBadge: 'Do AI tạo',
  rephraseCaution: 'AI chỉ diễn đạt lại. Hãy kiểm tra số liệu, tên công ty, công nghệ trước khi dùng.',
  rephraseUse: 'Dùng gợi ý',
  rephraseDismiss: 'Bỏ qua',
  rephraseSame: 'AI không đề xuất cách diễn đạt khác.',
  rephraseBusyOther: 'Đang tạo gợi ý cho một đoạn khác.',
  rephraseFailedFallback: 'Chưa tạo được gợi ý diễn đạt, vui lòng thử lại.',
  // Ba cau duoi trung nguyen van cau INVALID_CV_REPHRASE_REQUEST cua backend (REQUIREMENT 4.4, R-A2).
  rephraseTooShort: 'Đoạn mô tả cần ít nhất 20 ký tự để gợi ý diễn đạt.',
  rephraseTooLong: 'Gợi ý diễn đạt nhận đoạn mô tả tối đa 800 ký tự.',
  rephraseEmpty: 'Vui lòng nhập đoạn mô tả trước khi xin gợi ý.',
  retry: 'Thử lại',
  previewButton: 'Xem trước',
  previewRunning: 'Đang tạo bản xem trước…',
  previewTitle: 'Xem trước CV',
  previewNotice: 'Bản xem trước chưa được lưu. Sửa form rồi bấm "Xem trước" để xem lại.',
  previewFrameTitle: 'Bản xem trước CV',
  previewDownload: 'Tải bản xem trước',
  previewClose: 'Đóng',
  previewOpenCompact: 'Bản xem trước đã được tải về máy.',
  // L57 - loi B3 khong co {error, message} (mat mang, than loi khong phai JSON).
  previewFailedFallback: 'Chưa tạo được bản xem trước, vui lòng thử lại.',
  saveCreate: 'Lưu CV',
  saveEdit: 'Lưu thành CV mới',
  saving: 'Đang lưu…',
  savedCreate: 'Đã tạo CV.',
  savedEdit: 'Đã lưu thành một CV mới. CV cũ vẫn được giữ nguyên.',
  saveFailedFallback: 'Chưa lưu được CV, vui lòng thử lại.',
  resumeNotFound: 'Không tìm thấy CV này.',
  // L58 - B2 loi ngoai 400/403/404/409 (mat mang, 5xx).
  editLoadFailed: 'Không tải được CV này, vui lòng thử lại.',
} as const

// Khoa itemCounter: "{n}/{max} mục".
export function cvBuilderItemCounter(n: number, max: number): string {
  return `${n}/${max} mục`
}

// Khoa charCounter: "{n}/{max}" - so in khong dau cach nghin (L18).
export function cvBuilderCharCounter(n: number, max: number): string {
  return `${n}/${max}`
}

// Khoa moveUp / moveDown / removeItem (aria-label): "Đưa {khối} {i} lên" / "Đưa {khối} {i} xuống" / "Xoá {khối} {i}".
export function cvBuilderMoveUpLabel(block: CvBuilderBlock, i: number): string {
  return `Đưa ${blockInSentence(block)} ${i} lên`
}

export function cvBuilderMoveDownLabel(block: CvBuilderBlock, i: number): string {
  return `Đưa ${blockInSentence(block)} ${i} xuống`
}

export function cvBuilderRemoveItemLabel(block: CvBuilderBlock, i: number): string {
  return `Xoá ${blockInSentence(block)} ${i}`
}

// Khoa technologiesRemove (aria-label): "Xoá công nghệ {x}".
export function cvBuilderTechnologyRemoveLabel(technology: string): string {
  return `Xoá công nghệ ${technology}`
}

// Legend cua moi muc: "{Khối} {i}".
export function cvBuilderItemLegend(block: CvBuilderBlock, i: number): string {
  return `${CV_BUILDER_BLOCK_LABELS[block]} ${i}`
}

// "{khối}" viet thuong o giua cau (mau UI.md muc 7).
function blockInSentence(block: CvBuilderBlock): string {
  return CV_BUILDER_BLOCK_LABELS[block].toLocaleLowerCase('vi')
}

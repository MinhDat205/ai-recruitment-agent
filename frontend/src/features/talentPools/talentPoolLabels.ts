// FR-H15 - chuoi giao dien (UI.md muc 6, 7; L3). Cau loi ma backend hien nguyen van tu body (extractErrorMessage);
// cac cau o day chi la cau du phong khi khong co body (mat mang...).

// R-K2 - khop backend: strip() roi dem theo code point (TalentPoolNameRules).
export const TALENT_POOL_NAME_MAX = 100

export function countNameChars(value: string): number {
  return [...value.trim()].length
}

export const TALENT_POOL_TEXT = {
  navLabel: 'Kho ứng viên',
  navShortLabel: 'Kho',
  listTitle: 'Kho ứng viên',
  createPool: 'Tạo kho',
  rename: 'Đổi tên',
  deletePool: 'Xoá kho',
  save: 'Lưu',
  saving: 'Đang lưu…',
  cancel: 'Huỷ',
  remove: 'Gỡ',
  removing: 'Đang gỡ…',
  deleting: 'Đang xoá…',
  openApplication: 'Mở hồ sơ',
  messages: 'Trao đổi',
  removeFromPool: 'Gỡ khỏi kho',
  backToList: 'Kho ứng viên',
  backToListFromError: 'Về danh sách kho',
  retry: 'Thử lại',
  entryCount: (count: number) => `${count} hồ sơ`,
  jobDeleted: 'Tin đã xoá',
  appliedOn: (date: string) => `Nộp ${date}`,
  addedOn: (date: string) => `Thêm ${date}`,
  nameField: 'Tên kho',
  charCounter: (count: number) => `${count}/${TALENT_POOL_NAME_MAX}`,
  createDialogTitle: 'Tạo kho',
  renameDialogTitle: 'Đổi tên kho',
  deleteDialogTitle: 'Xoá kho',
  deleteConfirm: (name: string) =>
    `Xoá kho “${name}”? Các hồ sơ trong kho không bị xoá, chỉ không còn nằm trong kho này.`,
  removeDialogTitle: 'Gỡ khỏi kho',
  removeConfirm: (candidateName: string, poolName: string) =>
    `Gỡ hồ sơ của ${candidateName} khỏi kho “${poolName}”?`,
  // aria-label kem ten ung vien (UI.md muc 9).
  openApplicationLabel: (candidateName: string) => `Mở hồ sơ của ${candidateName}`,
  messagesLabel: (candidateName: string) => `Trao đổi với ${candidateName}`,
  removeFromPoolLabel: (candidateName: string) => `Gỡ hồ sơ của ${candidateName} khỏi kho`,
  // Hop thoai "Luu vao kho" (UI.md 4d, muc 7).
  saveToPool: 'Lưu vào kho',
  saveDialogTitle: (candidateName: string) => `Lưu hồ sơ của ${candidateName} vào kho`,
  close: 'Đóng',
  saved: 'Đã cập nhật kho ứng viên.',
} as const

// Cau cho don khong ACTIVE (UI.md muc 7). ACTIVE khong co cau.
export const TALENT_POOL_CONSENT_TEXT = {
  NOT_GIVEN: 'Ứng viên chưa đồng ý cho công ty lưu hồ sơ khi nộp đơn này nên không thể thêm vào kho.',
  REVOKED: 'Ứng viên đã rút đồng ý lưu hồ sơ của đơn này nên không thể thêm vào kho.',
} as const

export const TALENT_POOL_COLUMNS = {
  candidate: 'Ứng viên',
  job: 'Tin tuyển dụng',
  status: 'Trạng thái',
  appliedAt: 'Ngày nộp',
  addedAt: 'Ngày thêm',
  actions: 'Thao tác',
} as const

export const TALENT_POOL_STATE_TEXT = {
  listEmpty: 'Chưa có kho ứng viên nào.',
  listLoadFailed: 'Không tải được danh sách kho.',
  detailEmpty: 'Kho này chưa có hồ sơ nào. Mở trang hồ sơ đơn và bấm “Lưu vào kho” để thêm.',
  // 403 va 404 CUNG mot cau (UI_GUIDE muc 5) - khong lo kho co ton tai hay khong.
  notFound: 'Không tìm thấy kho ứng viên.',
  loadFailed: 'Không tải được dữ liệu. Vui lòng thử lại.',
  nameInvalid: 'Tên kho phải có từ 1 đến 100 ký tự.',
  actionFailed: 'Không thực hiện được thao tác. Vui lòng thử lại.',
} as const

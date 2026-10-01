// Chuan hoa de LOC danh sach trong combobox (UI.md FR-C05 muc 5): bo dau, d/D -> d, chu thuong, gop
// khoang trang - cung tinh than R-M1a o backend. CHI dung de tim trong danh sach hien thi; viec khop
// chuoi tu do sang ma (bo khop R-M3) la cua backend, frontend khong lam lai.
export function normalizeForSearch(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .replace(/[đĐ]/g, 'd')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim()
}

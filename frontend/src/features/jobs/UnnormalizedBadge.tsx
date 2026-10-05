// Nhan "Chua chuan hoa" (FR-C05 UI.md muc 5) - dung ca phia HR va phia cong khai/ung vien (FR-U07
// R-N3/R-N4, chi hien luc dang loc dung truong nganh/tinh - xem JobCard.tsx). Mau trung tinh, luon
// kem chu, khong do/vang: day la thong tin ve du lieu, khong phai canh bao loi.
export function UnnormalizedBadge() {
  return (
    <span className="inline-flex shrink-0 items-center rounded-m3-xs bg-m3-surface-container px-2 py-0.5 text-m3-label-md text-m3-on-surface">
      Chưa chuẩn hoá
    </span>
  )
}

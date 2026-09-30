// Linear progress KHONG xac dinh (UI_GUIDE muc 3; FR-C05 UI.md muc 5, 9): thanh bg-m3-primary chay tren
// nen bg-m3-primary-container, rong bang o chua no. Nguoi dung bat "giam chuyen dong" thi thanh dung yen.
export function LinearProgress({ label }: { label: string }) {
  return (
    <div
      role="progressbar"
      aria-label={label}
      className="relative h-1 w-full overflow-hidden rounded-m3-xs bg-m3-primary-container"
    >
      <div className="absolute inset-y-0 left-0 w-1/3 bg-m3-primary animate-m3-linear-progress motion-reduce:animate-none" />
    </div>
  )
}

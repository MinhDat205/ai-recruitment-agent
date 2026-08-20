// Badge dem so - component MOI, chua co tien le nao trong du an (cac "*Badge" khac deu la badge
// nhan trang thai, khong phai badge dem so). Dung token mau da khai trong @theme (bg-danger) va
// --radius-badge, khong hardcode ma hex.
export function NotificationBadge({ count }: { count: number }) {
  if (count <= 0) {
    return null
  }

  const label = count > 9 ? '9+' : String(count)

  return (
    <span
      aria-label={`${count} thông báo chưa đọc`}
      className="absolute -top-1 -right-1 flex h-4 min-w-4 items-center justify-center rounded-(--radius-badge) bg-danger px-1 text-[10px] font-semibold leading-none text-white"
    >
      {label}
    </span>
  )
}

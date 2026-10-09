// Backend serialize LocalDate dang "yyyy-MM-dd" (ISO). Toan app hien thi thong nhat dd/MM/yyyy.
export function formatDeadline(deadline: string | null | undefined): string {
  if (!deadline) {
    return 'Không giới hạn'
  }
  const [year, month, day] = deadline.split('-')
  if (!year || !month || !day) {
    return deadline
  }
  return `${day}/${month}/${year}`
}

// Instant ISO -> "dd/MM/yyyy" theo gio trinh duyet (vi-VN). Dung o trang ho so don (FR-H09) cho ngay
// cua lot cham hoan tat.
export function formatDateVi(iso: string): string {
  return new Date(iso).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

// Instant ISO -> "dd/MM/yyyy HH:mm" (vi-VN), cung dinh dang cot Ngay nop cua danh sach theo Job
// (ApplicationsTab.formatAppliedAt). Dung o dau trang ho so don (FR-H09).
export function formatDateTimeVi(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

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

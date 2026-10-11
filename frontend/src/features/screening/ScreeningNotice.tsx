import { Info } from 'lucide-react'
import { DialogDescription } from '@/components/ui/dialog'
import { SCREENING_TEXT } from './screeningLabels'

// Khoi luu y R-P1 - luon hien, khong co nut dong, mau trung tinh (UI.md 5d: khong m3-warning/m3-error).
// compact: ban rut gon lam mo ta cua hop thoai them/sua (UI.md 4c) - chi dung ben trong Dialog.
export function ScreeningNotice({ compact = false }: { compact?: boolean }) {
  if (compact) {
    return <DialogDescription>{SCREENING_TEXT.dialogDescription}</DialogDescription>
  }
  return (
    <div
      role="note"
      className="flex items-start gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container px-4 py-3 text-sm text-m3-on-surface"
    >
      <Info className="mt-0.5 h-4 w-4 shrink-0 text-m3-on-surface" aria-hidden="true" />
      <div className="flex min-w-0 flex-col gap-1 break-words">
        {SCREENING_TEXT.noticeLines.map((line) => (
          <p key={line}>{line}</p>
        ))}
      </div>
    </div>
  )
}

import { CalendarClock } from 'lucide-react'
import { ConfirmedSlotMark } from './ConfirmedSlotMark'
import { formatSlotTime, INVITATION_TEXT } from './invitationLabels'

export interface ReadOnlySlot {
  id: string
  displayOrder: number
  scheduledAt: string
  location: string | null
}

// FR-H12 UI.md 4c, 4d, 4f - danh sach khung CHI DOC, dung thu tu backend tra (displayOrder - thu tu HR nhap, R-S7),
// KHONG tu sap xep lai. <ol> vi thu tu co nghia (UI.md muc 9). Gio theo gio Viet Nam. Dia diem la CHU THUONG ke ca khi
// la link (B4) - khong <a>, khong render HTML. Dia diem thieu (du lieu cu) -> "Chua co du lieu".
// variant 'hr': "1. thoi diem - dia diem [Da chot]" (compact: dia diem + dau xuong dong duoi). variant 'candidate':
// icon lich + thoi diem [Da chot], dong "Dia diem: ..." (4d).
export function InterviewSlotList({
  slots,
  confirmedSlotId,
  variant,
}: {
  slots: ReadOnlySlot[]
  confirmedSlotId: string | null
  variant: 'hr' | 'candidate'
}) {
  return (
    <div className="flex min-w-0 flex-col gap-2">
      <h3 className="text-sm font-medium text-m3-on-surface">{INVITATION_TEXT.slotListTitle}</h3>
      <ol className="flex min-w-0 flex-col gap-2">
        {slots.map((slot) => {
          const confirmed = slot.id === confirmedSlotId
          const location = slot.location ?? INVITATION_TEXT.missingLocation
          if (variant === 'candidate') {
            return (
              <li key={slot.id} className="flex min-w-0 flex-col gap-1 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <CalendarClock className="h-4 w-4 shrink-0 text-m3-primary" aria-hidden="true" />
                  <span className="font-medium text-m3-on-surface">{formatSlotTime(slot.scheduledAt)}</span>
                  {confirmed && <ConfirmedSlotMark />}
                </div>
                <p className="pl-6 break-all text-m3-on-surface-variant">
                  <span className="font-medium text-m3-on-surface">Địa điểm: </span>
                  {location}
                </p>
              </li>
            )
          }
          return (
            <li key={slot.id} className="flex min-w-0 flex-col gap-1 text-sm sm:flex-row sm:flex-wrap sm:items-center sm:gap-x-2">
              <span className="font-medium text-m3-on-surface">
                {slot.displayOrder}. {formatSlotTime(slot.scheduledAt)}
              </span>
              <span className="hidden text-m3-on-surface-variant sm:inline" aria-hidden="true">
                ·
              </span>
              <span className="min-w-0 pl-4 break-all text-m3-on-surface-variant sm:pl-0">
                {location}
              </span>
              {confirmed && (
                <span className="pl-4 sm:pl-0">
                  <ConfirmedSlotMark />
                </span>
              )}
            </li>
          )
        })}
      </ol>
    </div>
  )
}

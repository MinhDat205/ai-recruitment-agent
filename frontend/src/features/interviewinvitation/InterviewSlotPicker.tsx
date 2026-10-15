import { CalendarClock } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { formatSlotTime, INVITATION_TEXT } from './invitationLabels'
import type { CandidateInterviewInvitationSlot } from './types'

interface InterviewSlotPickerProps {
  slots: CandidateInterviewInvitationSlot[]
  // Moc "gio may" do noi goi truyen vao (L31) - cung moc noi goi dung de quyet dinh hien phan chon (R-U1).
  nowMs: number
  // Bam "Xác nhận khung giờ" khi da chon - noi goi mo hop xac nhan (hop thoai nam o noi goi, khong o day: sau khi chon
  // thanh cong phan chon bi go, hop thoai van phai song toi khi dong han - L30).
  onRequestConfirm: (slot: CandidateInterviewInvitationSlot) => void
}

// FR-U10 UI.md 4a, 4e - phan chon khung cua ung vien. Radio goc trong <fieldset>/<legend> (khong co component radio o
// components/ui - REQUIREMENT a24). Thu tu khung = thu tu HR gui (displayOrder), khong sap lai. Khung da qua (theo gio may):
// radio disabled + chu "Đã qua", dong giu nguyen mau - khong opacity, khong gach ngang, khong mau canh bao. Khong chon san.
// Nut KHONG khoa khi chua chon (O4): bam khi chua chon -> cau loi + focus radio dau tien chon duoc, khong mo hop thoai.
// aria-invalid/aria-describedby dat tren vung role="radiogroup" chu khong tren <fieldset> (role group khong ho tro
// aria-invalid - L29).
export function InterviewSlotPicker({ slots, nowMs, onRequestConfirm }: InterviewSlotPickerProps) {
  const groupName = useId()
  const errorId = useId()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [showError, setShowError] = useState(false)
  const firstSelectableRef = useRef<HTMLInputElement>(null)

  const isElapsed = (slot: CandidateInterviewInvitationSlot) => new Date(slot.scheduledAt).getTime() <= nowMs
  // Khung dang chon ma vua thanh "da qua" (I4 tai lai sau 409 INTERVIEW_SLOT_PASSED) thi coi nhu chua chon.
  const selectedSlot = slots.find((slot) => slot.id === selectedId && !isElapsed(slot)) ?? null
  const firstSelectableId = slots.find((slot) => !isElapsed(slot))?.id ?? null

  const submit = () => {
    if (!selectedSlot) {
      setShowError(true)
      firstSelectableRef.current?.focus()
      return
    }
    onRequestConfirm(selectedSlot)
  }

  return (
    <div className="flex w-full min-w-0 flex-col gap-3">
      <fieldset className="flex min-w-0 flex-col gap-2">
        <legend className="mb-2 text-sm font-medium text-m3-on-surface">{INVITATION_TEXT.slotListTitle}</legend>
        <div
          role="radiogroup"
          aria-invalid={showError || undefined}
          aria-describedby={showError ? errorId : undefined}
          className="flex min-w-0 flex-col gap-1"
        >
          {slots.map((slot) => {
            const elapsed = isElapsed(slot)
            const checked = selectedSlot?.id === slot.id
            return (
              <label
                key={slot.id}
                className={cn(
                  'flex min-h-12 min-w-0 items-start gap-3 rounded-r-m3-sm border-l-2 px-2 py-2 text-sm',
                  checked ? 'border-m3-primary' : 'border-transparent',
                  elapsed ? 'cursor-default' : 'cursor-pointer hover:bg-m3-primary/8',
                )}
              >
                <input
                  ref={slot.id === firstSelectableId ? firstSelectableRef : undefined}
                  type="radio"
                  name={groupName}
                  value={slot.id}
                  checked={checked}
                  disabled={elapsed}
                  onChange={() => {
                    setSelectedId(slot.id)
                    setShowError(false)
                  }}
                  className="mt-0.5 h-4 w-4 shrink-0 accent-m3-primary"
                />
                <span className="flex min-w-0 flex-col gap-1">
                  <span className="flex flex-wrap items-center gap-2">
                    <CalendarClock className="h-4 w-4 shrink-0 text-m3-primary" aria-hidden="true" />
                    <span className="font-medium text-m3-on-surface">{formatSlotTime(slot.scheduledAt)}</span>
                    {elapsed && <span className="text-m3-on-surface-variant">{INVITATION_TEXT.slotElapsed}</span>}
                  </span>
                  <span className="break-all text-m3-on-surface-variant">
                    <span className="font-medium text-m3-on-surface">{INVITATION_TEXT.locationPrefix}</span>
                    {slot.location ?? INVITATION_TEXT.missingLocation}
                  </span>
                </span>
              </label>
            )
          })}
        </div>
      </fieldset>
      {showError && (
        <p id={errorId} role="alert" className="text-sm text-m3-error">
          {INVITATION_TEXT.slotRequired}
        </p>
      )}
      <Button type="button" onClick={submit} className="min-h-12 w-full sm:min-h-0 sm:w-auto sm:self-start">
        {INVITATION_TEXT.confirmSlotButton}
      </Button>
    </div>
  )
}

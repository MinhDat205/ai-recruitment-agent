import { Plus, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import {
  useFieldArray,
  type Control,
  type FieldErrors,
  type UseFormRegister,
  type UseFormSetFocus,
} from 'react-hook-form'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { INVITATION_TEXT } from './invitationLabels'

// Gia tri form cua hop thoai giay moi (InterviewInvitationDialog). scheduledAt la chuoi cua input datetime-local.
export interface InvitationFormValues {
  slots: { scheduledAt: string; location: string }[]
  subject: string
  content: string
}

// FR-H12 UI.md 4a, 4e, muc 9 - danh sach khung gio trong hop thoai. Thu tu = thu tu HR nhap (khong tu sap xep, R-S7).
// Gioi han so khung lay tu maxSlots cua backend (preview/I1), KHONG hardcode. Them khung: focus o thoi diem cua khung
// moi + doc "Da them khung gio {n}."; xoa khung: doc "Da xoa khung gio {n}.", focus ve nut xoa cua khung dung truoc
// (hoac o thoi diem cua khung 1 khi khong con nut xoa). Nut xoa KHONG render khi chi con mot khung.
export function InterviewSlotFields({
  control,
  register,
  errors,
  setFocus,
  maxSlots,
  disabled,
}: {
  control: Control<InvitationFormValues>
  register: UseFormRegister<InvitationFormValues>
  errors: FieldErrors<InvitationFormValues>
  setFocus: UseFormSetFocus<InvitationFormValues>
  maxSlots: number
  disabled: boolean
}) {
  const { fields, append, remove } = useFieldArray({ control, name: 'slots' })
  const [announcement, setAnnouncement] = useState('')
  const removeButtonRefs = useRef<(HTMLButtonElement | null)[]>([])
  // Viec dat focus SAU khi danh sach render lai: 'added' -> o thoi diem khung cuoi; so -> khung dung truoc da xoa.
  const pendingFocus = useRef<'added' | number | null>(null)
  const atLimit = fields.length >= maxSlots
  const limitHintId = 'invitation-slots-limit'

  useEffect(() => {
    const target = pendingFocus.current
    if (target === null) {
      return
    }
    pendingFocus.current = null
    if (target === 'added') {
      setFocus(`slots.${fields.length - 1}.scheduledAt`)
      return
    }
    const previousRemove = target >= 0 && fields.length > 1 ? removeButtonRefs.current[target] : null
    if (previousRemove) {
      previousRemove.focus()
    } else {
      setFocus('slots.0.scheduledAt')
    }
  }, [fields.length, setFocus])

  function handleAdd() {
    pendingFocus.current = 'added'
    append({ scheduledAt: '', location: '' }, { shouldFocus: false })
    setAnnouncement(INVITATION_TEXT.slotAdded(fields.length + 1))
  }

  function handleRemove(index: number) {
    pendingFocus.current = index - 1
    remove(index)
    setAnnouncement(INVITATION_TEXT.slotRemoved(index + 1))
  }

  return (
    <fieldset className="flex min-w-0 flex-col gap-3" disabled={disabled}>
      <legend className="text-sm font-medium text-m3-on-surface">{INVITATION_TEXT.slotsTitle}</legend>
      <p className="text-sm text-m3-on-surface-variant">{INVITATION_TEXT.slotsHint(maxSlots)}</p>

      <div className="flex flex-col gap-3">
        {fields.map((field, index) => {
          const n = index + 1
          const slotErrors = errors.slots?.[index]
          const timeId = `invitation-slot-${field.id}-time`
          const locationId = `invitation-slot-${field.id}-location`
          const timeErrorId = `${timeId}-error`
          const locationErrorId = `${locationId}-error`
          return (
            <fieldset
              key={field.id}
              className="relative flex min-w-0 flex-col gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-3"
            >
              {/* legend PHAI la con dau tien cua fieldset (UI.md muc 9) - nut xoa dat tuyet doi o goc tren phai. */}
              <legend className="float-left min-h-9 py-2 pr-14 text-sm font-medium text-m3-on-surface sm:pr-12">
                {INVITATION_TEXT.slotLegend(n)}
              </legend>
              {fields.length > 1 && (
                <Button
                  ref={(element) => {
                    removeButtonRefs.current[index] = element
                  }}
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="absolute top-1 right-1 size-12 text-m3-on-surface sm:size-9"
                  aria-label={INVITATION_TEXT.removeSlot(n)}
                  onClick={() => handleRemove(index)}
                >
                  <X className="h-4 w-4" aria-hidden="true" />
                </Button>
              )}
              <div className="clear-both grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="flex min-w-0 flex-col gap-1.5">
                  <Label htmlFor={timeId} className="text-m3-on-surface">
                    {INVITATION_TEXT.slotTimeLabel}
                  </Label>
                  <Input
                    id={timeId}
                    type="datetime-local"
                    aria-invalid={slotErrors?.scheduledAt ? true : undefined}
                    aria-describedby={slotErrors?.scheduledAt ? timeErrorId : undefined}
                    {...register(`slots.${index}.scheduledAt`)}
                  />
                  {slotErrors?.scheduledAt && (
                    <p id={timeErrorId} className="text-sm text-m3-error">
                      {slotErrors.scheduledAt.message}
                    </p>
                  )}
                </div>
                <div className="flex min-w-0 flex-col gap-1.5">
                  <Label htmlFor={locationId} className="text-m3-on-surface">
                    {INVITATION_TEXT.slotLocationLabel}
                  </Label>
                  <Input
                    id={locationId}
                    aria-required="true"
                    aria-invalid={slotErrors?.location ? true : undefined}
                    aria-describedby={slotErrors?.location ? locationErrorId : undefined}
                    {...register(`slots.${index}.location`)}
                  />
                  {slotErrors?.location && (
                    <p id={locationErrorId} className="text-sm text-m3-error">
                      {slotErrors.location.message}
                    </p>
                  )}
                </div>
              </div>
            </fieldset>
          )
        })}
      </div>

      <div className="flex flex-col items-stretch gap-1 sm:items-start">
        <Button
          type="button"
          variant="outline"
          className="w-full sm:w-auto"
          disabled={atLimit}
          aria-describedby={atLimit ? limitHintId : undefined}
          onClick={handleAdd}
        >
          <Plus className="h-4 w-4" aria-hidden="true" />
          {INVITATION_TEXT.addSlot}
        </Button>
        {atLimit && (
          <p id={limitHintId} className="text-sm text-m3-on-surface-variant">
            {INVITATION_TEXT.addSlotLimit(maxSlots)}
          </p>
        )}
      </div>

      <p aria-live="polite" className="sr-only">
        {announcement}
      </p>
    </fieldset>
  )
}

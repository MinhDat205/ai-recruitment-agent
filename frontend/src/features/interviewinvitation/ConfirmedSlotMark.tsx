import { Check } from 'lucide-react'
import { INVITATION_TEXT } from './invitationLabels'

// FR-H12 UI.md 5d - dau "Da chot": chu + icon Check (khong chi bang mau), cung cap mau voi badge INTERVIEW_INVITED.
// KHONG xanh la, khong do cho khung chua chot (CLAUDE.md muc 8).
export function ConfirmedSlotMark() {
  return (
    <span className="inline-flex shrink-0 items-center gap-1 rounded-(--radius-badge) bg-m3-primary-container px-2 py-0.5 text-xs font-medium text-m3-on-primary-container">
      <Check className="h-3.5 w-3.5" aria-hidden="true" />
      {INVITATION_TEXT.confirmed}
    </span>
  )
}

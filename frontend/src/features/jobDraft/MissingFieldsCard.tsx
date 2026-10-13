import { FIELD_LABELS, JOB_DRAFT_TEXT, NEED_LABELS, NEED_ORDER } from './jobDraftLabels'
import type { JobDraftField, MissingField } from './types'

// UI.md 4d (R-M) - khoi "Can bo sung" o dau Card wizard moi buoc che do AI, chi khi con muc. Danh sach do backend tinh
// (anh chup luc sinh - R-M4); noi goi da an muc ma HR da dien. Mau trung tinh - khong m3-warning/m3-error (khong phai loi).
// Moi ten truong la nut dua toi buoc chua truong do va focus o.
export function MissingFieldsCard({
  items,
  onSelect,
}: {
  items: MissingField[]
  onSelect: (field: JobDraftField) => void
}) {
  if (items.length === 0) {
    return null
  }
  return (
    <div
      role="note"
      aria-labelledby="job-draft-missing-title"
      className="flex flex-col gap-1.5 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container px-4 py-3 text-sm text-m3-on-surface"
    >
      <p id="job-draft-missing-title" className="font-medium">
        {JOB_DRAFT_TEXT.missingTitle}
      </p>
      {NEED_ORDER.map((need) => {
        const fields = items.filter((item) => item.need === need)
        if (fields.length === 0) {
          return null
        }
        return (
          <p key={need} className="flex flex-wrap items-center gap-x-1 break-words">
            <span>{NEED_LABELS[need]}</span>
            {fields.map((item, index) => (
              <span key={item.field} className="inline-flex items-center">
                <button
                  type="button"
                  onClick={() => onSelect(item.field)}
                  className="min-h-10 rounded-m3-xs px-1 text-m3-on-primary-container underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-m3-primary"
                >
                  {FIELD_LABELS[item.field]}
                </button>
                {index < fields.length - 1 && <span aria-hidden="true">,</span>}
              </span>
            ))}
          </p>
        )
      })}
      <p className="break-words">{JOB_DRAFT_TEXT.missingFixedLine}</p>
    </div>
  )
}

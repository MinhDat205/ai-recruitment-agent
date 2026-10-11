import { Check } from 'lucide-react'
import { useRef, type KeyboardEvent } from 'react'
import { SCREENING_QUESTION_TYPES, SCREENING_QUESTION_TYPE_LABELS } from './screeningLabels'
import type { ScreeningQuestionType } from './types'

// Segmented button 3 lua chon - cung cach lam voi ToneSegmentedControl (FR-C07) nhung la component rieng (UI.md 5c):
// role="radiogroup" + moi nut role="radio"/aria-checked, roving tabindex, phim mui ten doi lua chon, lua chon dang
// chon nhan ra bang icon Check (khong chi bang mau). Duoi sm xep doc ba nut (UI.md 4f, muc 8).
export function QuestionTypeSegmentedControl({
  value,
  onChange,
  labelledBy,
  disabled = false,
}: {
  value: ScreeningQuestionType
  onChange: (type: ScreeningQuestionType) => void
  labelledBy: string
  disabled?: boolean
}) {
  const buttonRefs = useRef<Array<HTMLButtonElement | null>>([])

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const step =
      event.key === 'ArrowRight' || event.key === 'ArrowDown'
        ? 1
        : event.key === 'ArrowLeft' || event.key === 'ArrowUp'
          ? -1
          : 0
    if (step === 0) {
      return
    }
    event.preventDefault()
    const next = (index + step + SCREENING_QUESTION_TYPES.length) % SCREENING_QUESTION_TYPES.length
    onChange(SCREENING_QUESTION_TYPES[next])
    buttonRefs.current[next]?.focus()
  }

  return (
    <div
      role="radiogroup"
      aria-labelledby={labelledBy}
      className="grid w-full grid-cols-1 overflow-hidden rounded-m3-sm border border-m3-outline sm:grid-cols-3"
    >
      {SCREENING_QUESTION_TYPES.map((type, index) => {
        const selected = type === value
        return (
          <button
            key={type}
            ref={(element) => {
              buttonRefs.current[index] = element
            }}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={selected ? 0 : -1}
            disabled={disabled}
            onClick={() => onChange(type)}
            onKeyDown={(event) => handleKeyDown(event, index)}
            className={`inline-flex min-h-12 items-center justify-center gap-1.5 border-m3-outline px-3 py-2 text-sm break-words disabled:opacity-60 sm:min-h-10 ${
              index === 0 ? '' : 'border-t sm:border-t-0 sm:border-l'
            } ${
              selected
                ? 'bg-m3-primary-container text-m3-on-primary-container'
                : 'bg-m3-surface text-m3-on-surface hover:bg-m3-primary/8'
            }`}
          >
            {selected && <Check className="h-4 w-4 shrink-0" aria-hidden="true" />}
            {SCREENING_QUESTION_TYPE_LABELS[type]}
          </button>
        )
      })}
    </div>
  )
}

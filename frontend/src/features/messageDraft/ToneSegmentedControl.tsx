import { useRef, type KeyboardEvent } from 'react'
import { Check } from 'lucide-react'
import { DRAFT_TEXT, DRAFT_TONE_LABELS } from './draftLabels'
import type { DraftTone } from './types'

const TONES: DraftTone[] = ['FORMAL', 'FRIENDLY']

// Segmented button 2 lua chon (UI_GUIDE muc 3, UI.md muc 5d, 9) dung bang <button> co san - khong sua components/ui.
// role="radiogroup" + moi nut role="radio"/aria-checked; phim mui ten doi lua chon (roving tabindex). Lua chon dang
// chon nhan ra bang icon Check, khong chi bang mau.
export function ToneSegmentedControl({
  value,
  onChange,
  disabled,
}: {
  value: DraftTone
  onChange: (tone: DraftTone) => void
  disabled: boolean
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
    const next = (index + step + TONES.length) % TONES.length
    onChange(TONES[next])
    buttonRefs.current[next]?.focus()
  }

  return (
    <div
      role="radiogroup"
      aria-label={DRAFT_TEXT.toneLabel}
      className="grid w-full grid-cols-2 sm:inline-grid sm:w-auto"
    >
      {TONES.map((tone, index) => {
        const selected = tone === value
        return (
          <button
            key={tone}
            ref={(element) => {
              buttonRefs.current[index] = element
            }}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={selected ? 0 : -1}
            disabled={disabled}
            onClick={() => onChange(tone)}
            onKeyDown={(event) => handleKeyDown(event, index)}
            className={`inline-flex min-h-10 items-center justify-center gap-1.5 border border-m3-outline px-3 py-2 text-sm break-words disabled:opacity-60 ${
              index === 0 ? 'rounded-l-m3-sm' : '-ml-px rounded-r-m3-sm'
            } ${
              selected
                ? 'bg-m3-primary-container text-m3-on-primary-container'
                : 'bg-m3-surface text-m3-on-surface hover:bg-m3-primary/8'
            }`}
          >
            {selected && <Check className="h-4 w-4 shrink-0" aria-hidden="true" />}
            {DRAFT_TONE_LABELS[tone]}
          </button>
        )
      })}
    </div>
  )
}

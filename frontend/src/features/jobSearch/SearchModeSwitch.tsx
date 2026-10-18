import { Check } from 'lucide-react'
import { useId } from 'react'
import { cn } from '@/lib/utils'
import { JOB_SEARCH_TEXT } from './jobSearchLabels'

export type SearchMode = 'keyword' | 'description'

const OPTIONS: { value: SearchMode; text: string; shortText: string }[] = [
  { value: 'keyword', text: JOB_SEARCH_TEXT.modeKeyword, shortText: JOB_SEARCH_TEXT.modeKeywordShort },
  { value: 'description', text: JOB_SEARCH_TEXT.modeDescription, shortText: JOB_SEARCH_TEXT.modeDescriptionShort },
]

// FR-U13 UI.md muc 5a, 5d, 8, 9 - bo chon che do: fieldset + legend sr-only + hai radio THUAN dang segmented (du an chua
// co component segmented - khuon TemplatePicker cua U12); phim mui ten doi lua chon theo hanh vi mac dinh cua trinh
// duyet. Lua chon dang chon co icon Check + chu, khong chi bang mau. Duoi sm: w-full, hai nua bang nhau, nhan ngan.
export function SearchModeSwitch({ value, onChange }: { value: SearchMode; onChange: (mode: SearchMode) => void }) {
  const name = useId()

  return (
    <fieldset className="flex w-full sm:w-auto">
      <legend className="sr-only">{JOB_SEARCH_TEXT.modeLegend}</legend>
      {OPTIONS.map((option, index) => {
        const checked = value === option.value
        return (
          <label
            key={option.value}
            className={cn(
              'flex min-h-12 flex-1 cursor-pointer items-center justify-center gap-2 border border-m3-outline px-4 text-m3-label-lg sm:min-h-10 sm:flex-none',
              'has-[input:focus-visible]:outline-2 has-[input:focus-visible]:outline-offset-2 has-[input:focus-visible]:outline-m3-primary',
              index === 0 ? 'rounded-l-m3-button' : '-ml-px rounded-r-m3-button',
              checked ? 'bg-m3-primary-container text-m3-on-primary-container' : 'bg-m3-surface text-m3-on-surface',
            )}
          >
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={checked}
              onChange={() => onChange(option.value)}
              className="sr-only"
            />
            {checked && <Check size={18} className="shrink-0" aria-hidden="true" />}
            <span className="sm:hidden">{option.shortText}</span>
            <span className="hidden sm:inline">{option.text}</span>
          </label>
        )
      })}
    </fieldset>
  )
}

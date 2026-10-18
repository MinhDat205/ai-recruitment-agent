import { useId } from 'react'
import { cn } from '@/lib/utils'
import { CV_BUILDER_TEXT } from './cvBuilderLabels'
import type { CvTemplate } from './types'

const OPTIONS: { value: CvTemplate; label: string }[] = [
  { value: 'CLASSIC', label: CV_BUILDER_TEXT.templateClassic },
  { value: 'MODERN', label: CV_BUILDER_TEXT.templateModern },
]

// FR-U12 UI.md muc 5a, 9 - chon mau trinh bay: fieldset + legend + hai radio THUAN (du an chua co RadioGroup trong
// components/ui); phim mui ten doi lua chon theo hanh vi mac dinh cua trinh duyet. Moi lua chon la mot khung bam duoc
// vien m3-outline; lua chon dang chon vien m3-primary 2px. Mo ta mau la chu, khong chi hinh.
export function TemplatePicker({ value, onChange }: { value: CvTemplate; onChange: (template: CvTemplate) => void }) {
  const name = useId()

  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="mb-3 text-m3-title-md text-m3-on-surface">{CV_BUILDER_TEXT.templateTitle}</legend>
      {OPTIONS.map((option) => {
        const checked = value === option.value
        return (
          <label
            key={option.value}
            className={cn(
              'flex min-h-10 cursor-pointer items-start gap-3 rounded-m3-sm bg-m3-surface p-3 text-sm text-m3-on-surface',
              checked ? 'border-2 border-m3-primary' : 'border border-m3-outline',
            )}
          >
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={checked}
              onChange={() => onChange(option.value)}
              className="mt-0.5 h-4 w-4 shrink-0 accent-m3-primary"
            />
            <span className="break-words">{option.label}</span>
          </label>
        )
      })}
    </fieldset>
  )
}

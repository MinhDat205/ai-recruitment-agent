import { X } from 'lucide-react'
import { useState, type KeyboardEvent } from 'react'
import { cn } from '@/lib/utils'

interface SkillTagInputProps {
  id: string
  value: string[]
  onChange: (skills: string[]) => void
  max: number
  placeholder: string
  maxReachedPlaceholder: string
  describedBy?: string
}

const MAX_SKILL_LENGTH = 50

// O nhap ky nang dang the (FR-U14 UI.md muc 4c, 9; REQUIREMENT R-K). Enter/dau phay them the,
// Backspace khi rong bo the cuoi, khu trung khong phan biet hoa/thuong nhung giu cach viet lan nhap
// DAU (R-K2) - the gia tri trung (so khop ha chu thuong) bi bo qua, khong tu sua lai gia tri cu.
export function SkillTagInput({
  id,
  value,
  onChange,
  max,
  placeholder,
  maxReachedPlaceholder,
  describedBy,
}: SkillTagInputProps) {
  const [draft, setDraft] = useState('')
  const [announcement, setAnnouncement] = useState('')
  const atMax = value.length >= max

  function addSkill(raw: string) {
    const trimmed = raw.trim()
    if (trimmed.length === 0 || trimmed.length > MAX_SKILL_LENGTH || value.length >= max) {
      return
    }
    const alreadyExists = value.some((skill) => skill.toLowerCase() === trimmed.toLowerCase())
    setDraft('')
    if (alreadyExists) {
      return
    }
    const next = [...value, trimmed]
    onChange(next)
    setAnnouncement(`Đã thêm ${trimmed}, còn ${max - next.length}/${max}`)
  }

  function removeSkill(skill: string) {
    const next = value.filter((item) => item !== skill)
    onChange(next)
    setAnnouncement(`Đã xoá ${skill}, còn ${max - next.length}/${max}`)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      addSkill(draft)
    } else if (event.key === 'Backspace' && draft.length === 0 && value.length > 0) {
      removeSkill(value[value.length - 1])
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <div
        className={cn(
          'flex min-h-12 flex-wrap items-center gap-2 rounded-m3-xs border border-m3-outline bg-m3-surface px-3 py-1.5 sm:min-h-10',
        )}
      >
        {value.map((skill) => (
          <span
            key={skill}
            className="flex h-8 items-center gap-1.5 rounded-m3-xs bg-m3-primary-container px-2.5 text-m3-label-md text-m3-on-primary-container"
          >
            {skill}
            <button
              type="button"
              onClick={() => removeSkill(skill)}
              aria-label={`Xoá kỹ năng ${skill}`}
              className="relative flex h-5 w-5 items-center justify-center rounded-full after:absolute after:-inset-3.5 after:content-['']"
            >
              <X className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          </span>
        ))}
        <input
          id={id}
          type="text"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={handleKeyDown}
          disabled={atMax}
          maxLength={MAX_SKILL_LENGTH}
          placeholder={atMax ? maxReachedPlaceholder : placeholder}
          aria-label="Nhập kỹ năng mới"
          aria-describedby={describedBy}
          className="h-8 min-w-[12ch] flex-1 bg-transparent text-m3-body-md text-m3-on-surface outline-none placeholder:text-m3-on-surface-variant disabled:cursor-not-allowed"
        />
      </div>
      <p aria-live="polite" className="sr-only">
        {announcement}
      </p>
    </div>
  )
}

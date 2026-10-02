import { ChevronDown, Search, X } from 'lucide-react'
import { useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { normalizeForSearch } from './normalize'
import type { CatalogItem } from './types'

interface CatalogMultiComboboxProps {
  id: string
  labelId: string
  value: string[]
  onChange: (codes: string[]) => void
  items: CatalogItem[] | undefined
  placeholder: string
  searchPlaceholder: string
  isLoading: boolean
  isError: boolean
  onRetry: () => void
  // FR-U14 UI.md muc 5 - gioi han so luong duoc chon (luon la 3 o dung dot nay, de gia tri thanh
  // tham so thay vi hang so cung de component dung duoc cho ca nganh nghe lan khu vuc).
  max: number
  describedBy?: string
}

// Combobox chon NHIEU muc danh muc (FR-U14 UI.md muc 5, 9) - dung tu Popover + o tim kiem cua
// CatalogCombobox (C05), nhung KHONG dong popover sau khi chon, moi dong co checkbox, chip da chon
// hien duoi trigger kem nut "x" rieng. Dat o day (khong phai candidateProfile/) vi la ha tang danh
// muc dung chung, giong CatalogCombobox.
export function CatalogMultiCombobox({
  id,
  labelId,
  value,
  onChange,
  items,
  placeholder,
  searchPlaceholder,
  isLoading,
  isError,
  onRetry,
  max,
  describedBy,
}: CatalogMultiComboboxProps) {
  const listboxId = useId()
  const optionIdPrefix = useId()
  const errorId = useId()
  const maxReachedId = useId()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const optionRefs = useRef<(HTMLLIElement | null)[]>([])

  const rows = useMemo<CatalogItem[]>(() => {
    const needle = normalizeForSearch(query)
    return (items ?? []).filter((item) => normalizeForSearch(item.label).includes(needle))
  }, [items, query])

  const disabled = isLoading || isError
  const atMax = value.length >= max
  const hasMatches = rows.length > 0

  function handleOpenChange(next: boolean) {
    setOpen(next)
    if (!next) {
      setQuery('')
      setActiveIndex(0)
    }
  }

  function toggle(row: CatalogItem) {
    const selected = value.includes(row.code)
    if (!selected && atMax) {
      return
    }
    const next = selected ? value.filter((code) => code !== row.code) : [...value, row.code]
    onChange(next)
    // Co y KHONG dong popover o day - khac CatalogCombobox (UI.md muc 5), cho chon tiep nhieu dong.
  }

  function remove(code: string) {
    onChange(value.filter((item) => item !== code))
  }

  function moveActive(next: number) {
    if (rows.length === 0) {
      return
    }
    const bounded = (next + rows.length) % rows.length
    setActiveIndex(bounded)
    optionRefs.current[bounded]?.scrollIntoView({ block: 'nearest' })
  }

  // Esc do Radix Popover xu ly: dong popover va tra focus ve nut trigger.
  function handleSearchKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      moveActive(activeIndex + 1)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      moveActive(activeIndex - 1)
    } else if (event.key === 'Enter') {
      event.preventDefault()
      const row = rows[activeIndex]
      if (row) {
        toggle(row)
      }
    }
  }

  function handleTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' && !open) {
      event.preventDefault()
      handleOpenChange(true)
    }
  }

  const triggerText = isLoading ? 'Đang tải danh mục…' : placeholder
  const describedByIds = [describedBy, isError ? errorId : undefined].filter(Boolean).join(' ') || undefined

  return (
    <div className="flex flex-col gap-1">
      <Popover open={open} onOpenChange={handleOpenChange}>
        <PopoverTrigger asChild>
          <button
            id={id}
            type="button"
            role="combobox"
            aria-haspopup="listbox"
            aria-expanded={open}
            aria-controls={listboxId}
            aria-labelledby={labelId}
            aria-describedby={describedByIds}
            disabled={disabled}
            onKeyDown={handleTriggerKeyDown}
            className={cn(
              'flex h-12 w-full items-center justify-between gap-2 rounded-m3-xs border border-m3-outline bg-m3-surface px-3 text-left text-m3-body-md text-m3-on-surface-variant sm:h-10',
              'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary',
              'disabled:cursor-not-allowed disabled:opacity-38',
            )}
          >
            <span className="truncate">{triggerText}</span>
            <ChevronDown className="h-4 w-4 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
          </button>
        </PopoverTrigger>
        <PopoverContent
          align="start"
          sideOffset={4}
          className="w-(--radix-popover-trigger-width) rounded-m3-md border border-m3-outline-variant bg-m3-surface p-0 text-m3-on-surface shadow-m3-3 ring-0"
        >
          <div className="flex items-center gap-2 border-b border-m3-outline-variant px-3">
            <Search className="h-4 w-4 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
            <input
              type="text"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value)
                setActiveIndex(0)
              }}
              onKeyDown={handleSearchKeyDown}
              placeholder={searchPlaceholder}
              aria-label={searchPlaceholder}
              aria-controls={listboxId}
              aria-autocomplete="list"
              aria-activedescendant={rows[activeIndex] ? `${optionIdPrefix}-${activeIndex}` : undefined}
              className="h-12 w-full bg-transparent text-m3-body-md text-m3-on-surface outline-none placeholder:text-m3-on-surface-variant sm:h-10"
            />
          </div>
          {!hasMatches && (
            <p className="px-3 py-3 text-m3-body-md text-m3-on-surface-variant">Không có mục phù hợp.</p>
          )}
          <ul
            id={listboxId}
            role="listbox"
            aria-labelledby={labelId}
            aria-multiselectable="true"
            className="max-h-[60vh] overflow-y-auto py-1 sm:max-h-80"
          >
            {rows.map((row, index) => {
              const selected = value.includes(row.code)
              const rowDisabled = !selected && atMax
              return (
                <li
                  key={row.code}
                  id={`${optionIdPrefix}-${index}`}
                  ref={(node) => {
                    optionRefs.current[index] = node
                  }}
                  role="option"
                  aria-selected={selected}
                  aria-disabled={rowDisabled}
                  onMouseEnter={() => setActiveIndex(index)}
                  // mousedown thay vi click: giu focus o o tim kiem, khong de popover dong truoc khi chon.
                  onMouseDown={(event) => {
                    event.preventDefault()
                    toggle(row)
                  }}
                  className={cn(
                    'flex min-h-12 items-center gap-3 px-3 text-m3-body-md sm:min-h-10',
                    rowDisabled ? 'cursor-not-allowed opacity-38' : 'cursor-pointer',
                    index === activeIndex && !rowDisabled && 'bg-m3-primary/8',
                  )}
                >
                  <Checkbox checked={selected} disabled={rowDisabled} tabIndex={-1} />
                  <span>{row.label}</span>
                </li>
              )
            })}
          </ul>
          {atMax && (
            <p
              id={maxReachedId}
              className="border-t border-m3-outline-variant px-3 py-2 text-m3-body-sm text-m3-on-surface-variant"
            >
              {`Đã chọn tối đa ${max} — bỏ một mục để chọn mục khác.`}
            </p>
          )}
        </PopoverContent>
      </Popover>
      {value.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {value.map((code) => {
            const label = items?.find((item) => item.code === code)?.label ?? code
            return (
              <span
                key={code}
                className="flex h-8 items-center gap-1.5 rounded-m3-xs bg-m3-primary-container px-2.5 text-m3-label-md text-m3-on-primary-container"
              >
                {label}
                <button
                  type="button"
                  onClick={() => remove(code)}
                  aria-label={`Bỏ chọn ${label}`}
                  className="relative flex h-5 w-5 items-center justify-center rounded-full after:absolute after:-inset-3.5 after:content-['']"
                >
                  <X className="h-3.5 w-3.5" aria-hidden="true" />
                </button>
              </span>
            )
          })}
        </div>
      )}
      {isError && (
        <p id={errorId} className="flex items-center gap-2 text-m3-body-sm text-m3-error">
          Không tải được danh mục. Vui lòng thử lại.
          <button type="button" onClick={onRetry} className="font-medium text-m3-primary underline">
            Thử lại
          </button>
        </p>
      )}
    </div>
  )
}

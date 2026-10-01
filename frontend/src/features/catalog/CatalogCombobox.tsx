import { Check, ChevronDown, Search } from 'lucide-react'
import { useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { normalizeForSearch } from './normalize'
import type { CatalogItem } from './types'

interface CatalogComboboxProps {
  id: string
  labelId: string
  value: string | null
  // "Bo chon" goi onChange(null) - KHONG BAO GIO gui chuoi rong: backend coi "" la ma khong co trong
  // danh muc va tra 400 INVALID_CATALOG_CODE (xem walkthrough FR-C05 dot 2).
  onChange: (code: string | null) => void
  items: CatalogItem[] | undefined
  placeholder: string
  searchPlaceholder: string
  isLoading: boolean
  isError: boolean
  onRetry: () => void
  describedBy?: string
}

// Moi dong trong listbox: mot muc danh muc, hoac dong "Bo chon" (code = null) o cuoi.
type OptionRow = { code: string | null; label: string }

const CLEAR_ROW: OptionRow = { code: null, label: 'Bỏ chọn' }

// Combobox chon mot muc danh muc (FR-C05 UI.md muc 5, 9). Dung tu Popover + o tim kiem, khong them thu
// vien. Khong cho nhap tu do: chi chon duoc ma co trong danh sach (UI.md muc 10).
export function CatalogCombobox({
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
  describedBy,
}: CatalogComboboxProps) {
  const listboxId = useId()
  const optionIdPrefix = useId()
  const errorId = useId()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const optionRefs = useRef<(HTMLLIElement | null)[]>([])

  const rows = useMemo<OptionRow[]>(() => {
    const needle = normalizeForSearch(query)
    const matched = (items ?? []).filter((item) => normalizeForSearch(item.label).includes(needle))
    return [...matched, CLEAR_ROW]
  }, [items, query])

  const selectedLabel = items?.find((item) => item.code === value)?.label ?? value
  const disabled = isLoading || isError
  const hasMatches = rows.length > 1

  function handleOpenChange(next: boolean) {
    setOpen(next)
    if (!next) {
      setQuery('')
      setActiveIndex(0)
    }
  }

  function select(row: OptionRow) {
    onChange(row.code)
    handleOpenChange(false)
  }

  function moveActive(next: number) {
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
        select(row)
      }
    }
  }

  function handleTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' && !open) {
      event.preventDefault()
      handleOpenChange(true)
    }
  }

  const triggerText = isLoading ? 'Đang tải danh mục…' : (selectedLabel ?? placeholder)
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
              'flex h-12 w-full items-center justify-between gap-2 rounded-m3-xs border border-m3-outline bg-m3-surface px-3 text-left text-m3-body-md sm:h-10',
              'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary',
              'disabled:cursor-not-allowed disabled:opacity-38',
              selectedLabel && !isLoading ? 'text-m3-on-surface' : 'text-m3-on-surface-variant',
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
              aria-activedescendant={`${optionIdPrefix}-${activeIndex}`}
              className="h-12 w-full bg-transparent text-m3-body-md text-m3-on-surface outline-none placeholder:text-m3-on-surface-variant sm:h-10"
            />
          </div>
          {!hasMatches && (
            <p className="px-3 py-3 text-m3-body-md text-m3-on-surface-variant">Không có mục phù hợp.</p>
          )}
          <ul id={listboxId} role="listbox" aria-labelledby={labelId} className="max-h-[60vh] overflow-y-auto py-1 sm:max-h-80">
            {rows.map((row, index) => {
              const isClear = row.code === null
              const selected = !isClear && row.code === value
              return (
                <li
                  key={row.code ?? '__clear__'}
                  id={`${optionIdPrefix}-${index}`}
                  ref={(node) => {
                    optionRefs.current[index] = node
                  }}
                  role="option"
                  aria-selected={selected}
                  onMouseEnter={() => setActiveIndex(index)}
                  // mousedown thay vi click: giu focus o o tim kiem, khong de popover dong truoc khi chon.
                  onMouseDown={(event) => {
                    event.preventDefault()
                    select(row)
                  }}
                  className={cn(
                    'flex min-h-12 cursor-pointer items-center justify-between gap-2 px-3 text-m3-body-md sm:min-h-10',
                    index === activeIndex && 'bg-m3-primary/8',
                    isClear && 'border-t border-m3-outline-variant text-m3-on-surface-variant',
                  )}
                >
                  <span>{row.label}</span>
                  {selected && <Check className="h-4 w-4 shrink-0 text-m3-primary" aria-hidden="true" />}
                </li>
              )
            })}
          </ul>
        </PopoverContent>
      </Popover>
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

import { ChevronDown, Search } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { CatalogCombobox } from '../catalog/CatalogCombobox'
import { WORK_MODE_LABELS, WORK_MODE_OPTIONS } from './jobLabels'
import type { JobSort, PostedWithin, WorkMode } from './types'
import { useJobFilters } from './useJobFilters'

const POSTED_WITHIN_LABELS: Record<PostedWithin, string> = {
  LAST_24H: '24 giờ qua',
  LAST_7D: '7 ngày qua',
  LAST_30D: '30 ngày qua',
}

const SORT_LABELS: Record<JobSort, string> = {
  NEWEST: 'Mới nhất',
  SALARY_DESC: 'Lương cao nhất',
}

// Class dung chung cho moi trigger dang "chip" trong thanh loc (combobox, popover, select) - cung
// chieu cao/vien/token m3 nhu CatalogCombobox (UI.md muc 5).
const FIELD_TRIGGER_CLASS =
  'flex h-10 items-center gap-2 rounded-m3-xs border border-m3-outline bg-m3-surface px-3 text-m3-body-md text-m3-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary'

interface JobFilterBarProps {
  totalElements: number | undefined
}

// Thanh loc tim viec (FR-U07 UI.md muc 4a) - thay HeroSearch.tsx. Doc/ghi toan bo trang thai qua
// useJobFilters (URL la nguon su that duy nhat, R-U1) - component nay KHONG giu state loc rieng,
// chi giu state "dang go/dang chon trong popover" truoc khi Ap dung (R-U6).
export function JobFilterBar({ totalElements }: JobFilterBarProps) {
  const { filters, catalogsQuery, applyFilter, clearFilters, hasActiveFilters } = useJobFilters()

  return (
    <div className="rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-4">
      <div className="flex flex-wrap items-center gap-3">
        {/* key=filters.keyword: URL doi tu ben ngoai (back/forward, dan link - R-U2) lam component
            remount, dua "draft" (state cuc bo truoc khi Enter/bam Tim kiem) ve dung gia tri URL -
            khong dung useEffect de gan lai state (tranh canh bao set-state-in-effect). */}
        <KeywordField key={filters.keyword} keyword={filters.keyword} onApply={(keyword) => applyFilter({ keyword })} />

        <CategoryField
          value={filters.categoryCode}
          onChange={(categoryCode) => applyFilter({ categoryCode })}
          catalogsQuery={catalogsQuery}
        />

        <LocationField
          value={filters.locationCode}
          onChange={(locationCode) => applyFilter({ locationCode })}
          catalogsQuery={catalogsQuery}
        />

        <SalaryField
          salaryMin={filters.salaryMin}
          salaryMax={filters.salaryMax}
          hideUnlisted={filters.hideUnlisted}
          onApply={(patch) => applyFilter(patch)}
        />

        <WorkModeField value={filters.workMode} onChange={(workMode) => applyFilter({ workMode })} />

        <PostedWithinField
          value={filters.postedWithin}
          onChange={(postedWithin) => applyFilter({ postedWithin })}
        />

        {hasActiveFilters && (
          <button
            type="button"
            onClick={clearFilters}
            className="flex h-10 items-center justify-center rounded-m3-button border border-m3-outline px-4 text-m3-label-lg text-m3-on-surface"
          >
            Xoá bộ lọc
          </button>
        )}
      </div>

      <div className="mt-3 flex flex-wrap items-center justify-between gap-3 border-t border-m3-outline-variant pt-3">
        <p aria-live="polite" className="text-m3-body-sm text-m3-on-surface-variant">
          {totalElements != null ? `Tìm thấy ${totalElements} việc làm` : ' '}
        </p>
        <SortField value={filters.sort} onChange={(sort) => applyFilter({ sort })} />
      </div>
    </div>
  )
}

function KeywordField({ keyword, onApply }: { keyword: string; onApply: (value: string) => void }) {
  const [draft, setDraft] = useState(keyword)
  const labelId = useId()
  const inputId = useId()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onApply(draft)
  }

  return (
    <form onSubmit={handleSubmit} className="flex min-w-[260px] flex-1 items-center gap-2">
      <Label id={labelId} htmlFor={inputId} className="sr-only">
        Từ khoá
      </Label>
      <div className="flex h-10 flex-1 items-center gap-2 rounded-m3-xs border border-m3-outline bg-m3-surface px-3">
        <Search className="h-4 w-4 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
        <input
          id={inputId}
          type="text"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="Tìm theo tên vị trí tuyển dụng..."
          aria-labelledby={labelId}
          className="w-full bg-transparent text-m3-body-md text-m3-on-surface outline-none placeholder:text-m3-on-surface-variant"
        />
      </div>
      <button
        type="submit"
        className="flex h-10 shrink-0 items-center justify-center rounded-m3-button bg-m3-primary px-4 text-m3-label-lg text-m3-on-primary"
      >
        Tìm kiếm
      </button>
    </form>
  )
}

interface CatalogFilterFieldProps {
  value: string | null
  onChange: (code: string | null) => void
  catalogsQuery: {
    data: { industries: { code: string; label: string }[]; provinces: { code: string; label: string }[] } | undefined
    isLoading: boolean
    isError: boolean
    refetch: () => void
  }
}

function CategoryField({ value, onChange, catalogsQuery }: CatalogFilterFieldProps) {
  const labelId = useId()
  const id = useId()
  return (
    <div className="flex min-w-[200px] flex-col gap-1">
      <Label id={labelId} htmlFor={id} className="sr-only">
        Ngành nghề
      </Label>
      <CatalogCombobox
        id={id}
        labelId={labelId}
        value={value}
        onChange={onChange}
        items={catalogsQuery.data?.industries}
        placeholder="Chọn ngành nghề"
        searchPlaceholder="Tìm ngành nghề..."
        isLoading={catalogsQuery.isLoading}
        isError={catalogsQuery.isError}
        onRetry={() => catalogsQuery.refetch()}
      />
    </div>
  )
}

function LocationField({ value, onChange, catalogsQuery }: CatalogFilterFieldProps) {
  const labelId = useId()
  const id = useId()
  return (
    <div className="flex min-w-[200px] flex-col gap-1">
      <Label id={labelId} htmlFor={id} className="sr-only">
        Tỉnh/thành
      </Label>
      <CatalogCombobox
        id={id}
        labelId={labelId}
        value={value}
        onChange={onChange}
        items={catalogsQuery.data?.provinces}
        placeholder="Chọn tỉnh/thành"
        searchPlaceholder="Tìm tỉnh/thành..."
        isLoading={catalogsQuery.isLoading}
        isError={catalogsQuery.isError}
        onRetry={() => catalogsQuery.refetch()}
      />
    </div>
  )
}

interface SalaryFieldProps {
  salaryMin: number | null
  salaryMax: number | null
  hideUnlisted: boolean
  onApply: (patch: { salaryMin: number | null; salaryMax: number | null; hideUnlisted: boolean }) => void
}

// Popover luong (UI.md muc 4a, 10): chi ghi vao URL khi bam "Ap dung" - khong loc theo tung phim go
// so. Draft dong bo lai tu URL moi lan MO popover (R-U2), khong dong bo lien tuc de khong mat du
// lieu dang go.
function SalaryField({ salaryMin, salaryMax, hideUnlisted, onApply }: SalaryFieldProps) {
  const [open, setOpen] = useState(false)
  const [minDraft, setMinDraft] = useState(salaryMin != null ? String(salaryMin) : '')
  const [maxDraft, setMaxDraft] = useState(salaryMax != null ? String(salaryMax) : '')
  const [hideUnlistedDraft, setHideUnlistedDraft] = useState(hideUnlisted)
  const errorId = useId()

  function handleOpenChange(next: boolean) {
    setOpen(next)
    if (next) {
      setMinDraft(salaryMin != null ? String(salaryMin) : '')
      setMaxDraft(salaryMax != null ? String(salaryMax) : '')
      setHideUnlistedDraft(hideUnlisted)
    }
  }

  const minValue = minDraft.trim() === '' ? null : Number(minDraft)
  const maxValue = maxDraft.trim() === '' ? null : Number(maxDraft)
  const minInvalid = minValue != null && (!Number.isFinite(minValue) || minValue < 0)
  const maxInvalid = maxValue != null && (!Number.isFinite(maxValue) || maxValue < 0)
  const rangeInvalid = !minInvalid && !maxInvalid && minValue != null && maxValue != null && minValue > maxValue
  const hasError = minInvalid || maxInvalid || rangeInvalid

  const errorMessage = rangeInvalid
    ? 'Lương tối thiểu phải nhỏ hơn hoặc bằng lương tối đa.'
    : minInvalid || maxInvalid
      ? 'Lương phải là số không âm.'
      : null

  function handleApply() {
    if (hasError) return
    onApply({ salaryMin: minValue, salaryMax: maxValue, hideUnlisted: hideUnlistedDraft })
    setOpen(false)
  }

  const triggerText =
    salaryMin != null || salaryMax != null
      ? `Lương: ${salaryMin ?? 0} - ${salaryMax != null ? salaryMax : '∞'} triệu`
      : 'Mức lương'

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>
        <button type="button" aria-expanded={open} className={FIELD_TRIGGER_CLASS}>
          {triggerText}
          <ChevronDown className="h-4 w-4 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
        </button>
      </PopoverTrigger>
      <PopoverContent
        align="start"
        className="w-72 rounded-m3-md border border-m3-outline-variant bg-m3-surface p-4 text-m3-on-surface shadow-m3-3"
      >
        <p className="text-m3-label-md text-m3-on-surface">Mức lương (triệu VNĐ)</p>
        <div className="mt-2 flex items-center gap-2">
          <label className="flex h-10 flex-1 items-center gap-1 rounded-m3-xs border border-m3-outline bg-m3-surface px-2">
            <span className="text-m3-body-sm text-m3-on-surface-variant">Từ</span>
            <input
              type="number"
              min={0}
              inputMode="numeric"
              value={minDraft}
              onChange={(event) => setMinDraft(event.target.value)}
              aria-describedby={hasError ? errorId : undefined}
              className="w-full bg-transparent text-right text-m3-body-md text-m3-on-surface outline-none"
            />
            <span className="text-m3-body-sm text-m3-on-surface-variant">triệu</span>
          </label>
          <label className="flex h-10 flex-1 items-center gap-1 rounded-m3-xs border border-m3-outline bg-m3-surface px-2">
            <span className="text-m3-body-sm text-m3-on-surface-variant">Đến</span>
            <input
              type="number"
              min={0}
              inputMode="numeric"
              value={maxDraft}
              onChange={(event) => setMaxDraft(event.target.value)}
              aria-describedby={hasError ? errorId : undefined}
              className="w-full bg-transparent text-right text-m3-body-md text-m3-on-surface outline-none"
            />
            <span className="text-m3-body-sm text-m3-on-surface-variant">triệu</span>
          </label>
        </div>
        {errorMessage && (
          <p id={errorId} className="mt-2 text-m3-body-sm text-m3-error">
            {errorMessage}
          </p>
        )}
        <label className="mt-3 flex items-center gap-2 text-m3-body-md text-m3-on-surface">
          <Checkbox
            checked={hideUnlistedDraft}
            onCheckedChange={(checked) => setHideUnlistedDraft(checked === true)}
          />
          Ẩn tin không công bố lương
        </label>
        <button
          type="button"
          onClick={handleApply}
          disabled={hasError}
          className="mt-3 flex h-10 w-full items-center justify-center rounded-m3-button bg-m3-primary text-m3-label-lg text-m3-on-primary disabled:cursor-not-allowed disabled:opacity-38"
        >
          Áp dụng
        </button>
      </PopoverContent>
    </Popover>
  )
}

function WorkModeField({ value, onChange }: { value: WorkMode[]; onChange: (next: WorkMode[]) => void }) {
  const [open, setOpen] = useState(false)

  function toggle(mode: WorkMode) {
    const next = value.includes(mode) ? value.filter((item) => item !== mode) : [...value, mode]
    onChange(next)
  }

  const triggerText = value.length > 0 ? `Hình thức (${value.length})` : 'Hình thức'

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button type="button" aria-expanded={open} className={FIELD_TRIGGER_CLASS}>
          {triggerText}
          <ChevronDown className="h-4 w-4 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
        </button>
      </PopoverTrigger>
      <PopoverContent
        align="start"
        className="w-56 rounded-m3-md border border-m3-outline-variant bg-m3-surface p-2 text-m3-on-surface shadow-m3-3"
      >
        <div className="flex flex-col gap-1">
          {WORK_MODE_OPTIONS.map((mode) => {
            const checked = value.includes(mode as WorkMode)
            return (
              <label
                key={mode}
                className={cn(
                  'flex min-h-10 cursor-pointer items-center gap-3 rounded-m3-xs px-2 text-m3-body-md text-m3-on-surface',
                  checked && 'bg-m3-primary/8',
                )}
              >
                <Checkbox checked={checked} onCheckedChange={() => toggle(mode as WorkMode)} />
                {WORK_MODE_LABELS[mode]}
              </label>
            )
          })}
        </div>
      </PopoverContent>
    </Popover>
  )
}

function PostedWithinField({
  value,
  onChange,
}: {
  value: PostedWithin | null
  onChange: (value: PostedWithin | null) => void
}) {
  const labelId = useId()
  const id = useId()
  return (
    <div className="flex flex-col gap-1">
      <Label id={labelId} htmlFor={id} className="sr-only">
        Thời gian đăng
      </Label>
      <select
        id={id}
        value={value ?? ''}
        onChange={(event) => onChange(event.target.value === '' ? null : (event.target.value as PostedWithin))}
        className={FIELD_TRIGGER_CLASS}
      >
        <option value="">Mọi thời điểm</option>
        {(Object.keys(POSTED_WITHIN_LABELS) as PostedWithin[]).map((key) => (
          <option key={key} value={key}>
            {POSTED_WITHIN_LABELS[key]}
          </option>
        ))}
      </select>
    </div>
  )
}

function SortField({ value, onChange }: { value: JobSort; onChange: (value: JobSort) => void }) {
  const labelId = useId()
  const id = useId()
  return (
    <div className="flex items-center gap-2">
      <Label id={labelId} htmlFor={id} className="text-m3-body-sm text-m3-on-surface-variant">
        Sắp xếp
      </Label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value as JobSort)}
        className={FIELD_TRIGGER_CLASS}
      >
        {(Object.keys(SORT_LABELS) as JobSort[]).map((key) => (
          <option key={key} value={key}>
            {SORT_LABELS[key]}
          </option>
        ))}
      </select>
    </div>
  )
}

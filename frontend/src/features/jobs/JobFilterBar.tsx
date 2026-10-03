import { ChevronDown, Search, SlidersHorizontal, X } from 'lucide-react'
import { Dialog as DialogPrimitive } from 'radix-ui'
import { useId, useState, type FormEvent } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { CatalogMultiCombobox } from '../catalog/CatalogMultiCombobox'
import { WORK_MODE_LABELS, WORK_MODE_OPTIONS } from './jobLabels'
import type { JobSort, PostedWithin, WorkMode } from './types'
import { useJobFilters, type JobFilterPatch, type JobFiltersState } from './useJobFilters'

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
// chieu cao/vien/token m3 nhu CatalogCombobox (UI.md muc 5). h-12->sm:h-10: vung cham >=48px duoi
// "sm" (UI_GUIDE muc 1h), giong dung mau CatalogCombobox da dung.
const FIELD_TRIGGER_CLASS =
  'flex h-12 items-center gap-2 rounded-m3-xs border border-m3-outline bg-m3-surface px-3 text-m3-body-md text-m3-on-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary sm:h-10'

interface JobFilterBarProps {
  totalElements: number | undefined
}

// Thanh loc tim viec (FR-U07 UI.md muc 4a, 4b, 8) - thay HeroSearch.tsx. Doc/ghi toan bo trang
// thai qua useJobFilters (URL la nguon su that duy nhat, R-U1) - component nay KHONG giu state loc
// rieng, chi giu state "dang go/dang chon" truoc khi Ap dung (R-U6).
//
// Hai bo khung rieng theo breakpoint (UI_GUIDE "Compact (< sm)" vs con lai), KHONG dung 1 bo logic
// chung: duoi sm moi dieu kien (tru tu khoa, sap xep) phai gom vao bottom sheet va CHI ghi URL khi
// bam "Ap dung" trong sheet (UI.md muc 4b) - khac han desktop (tung dieu kien ap dung ngay, R-U6).
export function JobFilterBar({ totalElements }: JobFilterBarProps) {
  const { filters, catalogsQuery, applyFilter, clearFilters, hasActiveFilters, activeFilterCount } = useJobFilters()
  const countText = totalElements != null ? `Tìm thấy ${totalElements} việc làm` : ' '

  return (
    <div className="rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-4">
      {/* ===== >= sm: day du cac control tren 1 thanh, ap dung ngay (tru tu khoa/luong, R-U6) ===== */}
      <div className="hidden sm:block">
        <div className="flex flex-wrap items-center gap-3">
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
            {countText}
          </p>
          <SortField value={filters.sort} onChange={(sort) => applyFilter({ sort })} />
        </div>
      </div>

      {/* ===== < sm (UI.md muc 4b, 8): thu gon thanh nut "Loc", tu khoa + sap xep dung ngoai sheet ===== */}
      <div className="flex flex-col gap-3 sm:hidden">
        <KeywordField key={`m-${filters.keyword}`} keyword={filters.keyword} onApply={(keyword) => applyFilter({ keyword })} />

        <div className="flex items-center justify-between gap-3">
          <MobileFilterSheet
            filters={filters}
            catalogsQuery={catalogsQuery}
            applyFilter={applyFilter}
            clearFilters={clearFilters}
            activeFilterCount={activeFilterCount}
          />
          <SortField value={filters.sort} onChange={(sort) => applyFilter({ sort })} />
        </div>

        <p aria-live="polite" className="border-t border-m3-outline-variant pt-3 text-m3-body-sm text-m3-on-surface-variant">
          {countText}
        </p>
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
      <div className="flex h-12 flex-1 items-center gap-2 rounded-m3-xs border border-m3-outline bg-m3-surface px-3 sm:h-10">
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
        className="flex h-12 shrink-0 items-center justify-center rounded-m3-button bg-m3-primary px-4 text-m3-label-lg text-m3-on-primary sm:h-10"
      >
        Tìm kiếm
      </button>
    </form>
  )
}

interface CatalogFilterFieldProps {
  value: string[]
  onChange: (codes: string[]) => void
  catalogsQuery: {
    data: { industries: { code: string; label: string }[]; provinces: { code: string; label: string }[] } | undefined
    isLoading: boolean
    isError: boolean
    refetch: () => void
  }
}

// FR-U15 R-F2 - CatalogMultiCombobox (co san tu FR-U14) thay CatalogCombobox (chon 1) - toi da 3
// ma (max={3}, khop R-H4 backend). KHONG sua ben trong CatalogMultiCombobox.
function CategoryField({ value, onChange, catalogsQuery }: CatalogFilterFieldProps) {
  const labelId = useId()
  const id = useId()
  return (
    <div className="flex min-w-[200px] flex-col gap-1">
      <Label id={labelId} htmlFor={id} className="sr-only">
        Ngành nghề
      </Label>
      <CatalogMultiCombobox
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
        max={3}
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
      <CatalogMultiCombobox
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
        max={3}
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

// Tinh toan validate/thong bao loi dung chung cho popover luong desktop VA bottom sheet mobile -
// tranh viet lai 2 lan cung mot cong thuc (R-S3, UI.md muc 6 "Loi luong").
function useSalaryDraftValidation(minDraft: string, maxDraft: string) {
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
  return { minValue, maxValue, hasError, errorMessage }
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

  const { minValue, maxValue, hasError, errorMessage } = useSalaryDraftValidation(minDraft, maxDraft)

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
      <Label id={labelId} htmlFor={id} className="hidden text-m3-body-sm text-m3-on-surface-variant sm:inline">
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

interface MobileFilterSheetProps {
  filters: JobFiltersState
  catalogsQuery: CatalogFilterFieldProps['catalogsQuery']
  applyFilter: (patch: JobFilterPatch) => void
  clearFilters: () => void
  activeFilterCount: number
}

// Bottom sheet loc (UI.md muc 4b, 8, 9) - dung DialogPrimitive (radix-ui, da la dependency san co
// qua components/ui/dialog.tsx va sheet.tsx, KHONG phai them thu vien moi) truc tiep thay vi 2
// wrapper do vi ca hai dinh vi giua man hinh (dialog) hoac truot tu PHAI (sheet) - khong hop voi
// "truot tu duoi, cao toi da 85vh" cua FR nay; tu viet class vi tri + token m3 don gian hon co
// gang override class dinh vi co san cua 2 wrapper do. Radix Dialog mac dinh da dam: role="dialog"
// aria-modal, bay focus trong content, tra focus ve trigger luc dong, Esc/bam ngoai dong KHONG ap
// dung thay doi chua luu (component chi goi applyFilter trong handleApply, khong goi o onOpenChange
// khi dong) - dung UI.md muc 9 ma khong can code them.
function MobileFilterSheet({ filters, catalogsQuery, applyFilter, clearFilters, activeFilterCount }: MobileFilterSheetProps) {
  const [open, setOpen] = useState(false)
  const [categoryDraft, setCategoryDraft] = useState<string[]>(filters.categoryCode)
  const [locationDraft, setLocationDraft] = useState<string[]>(filters.locationCode)
  const [minDraft, setMinDraft] = useState(filters.salaryMin != null ? String(filters.salaryMin) : '')
  const [maxDraft, setMaxDraft] = useState(filters.salaryMax != null ? String(filters.salaryMax) : '')
  const [hideUnlistedDraft, setHideUnlistedDraft] = useState(filters.hideUnlisted)
  const [workModeDraft, setWorkModeDraft] = useState<WorkMode[]>(filters.workMode)
  const [postedWithinDraft, setPostedWithinDraft] = useState<PostedWithin | null>(filters.postedWithin)
  const salaryErrorId = useId()

  function handleOpenChange(next: boolean) {
    setOpen(next)
    if (next) {
      // Dong bo lai toan bo draft tu URL hien tai moi lan MO sheet (R-U2) - dong (X/Esc/bam ngoai)
      // KHONG lam gi them, draft bi vut bo tu nhien vi khong con duoc hien (UI.md muc 4b, 10).
      setCategoryDraft(filters.categoryCode)
      setLocationDraft(filters.locationCode)
      setMinDraft(filters.salaryMin != null ? String(filters.salaryMin) : '')
      setMaxDraft(filters.salaryMax != null ? String(filters.salaryMax) : '')
      setHideUnlistedDraft(filters.hideUnlisted)
      setWorkModeDraft(filters.workMode)
      setPostedWithinDraft(filters.postedWithin)
    }
  }

  const { minValue, maxValue, hasError, errorMessage } = useSalaryDraftValidation(minDraft, maxDraft)

  function toggleWorkMode(mode: WorkMode) {
    setWorkModeDraft((prev) => (prev.includes(mode) ? prev.filter((item) => item !== mode) : [...prev, mode]))
  }

  // Mot lan "Ap dung" = DUNG MOT lan goi applyFilter (-> 1 lan setSearchParams, R-U6) du doi ca 5
  // nhom dieu kien cung luc.
  function handleApply() {
    if (hasError) return
    applyFilter({
      categoryCode: categoryDraft,
      locationCode: locationDraft,
      salaryMin: minValue,
      salaryMax: maxValue,
      hideUnlisted: hideUnlistedDraft,
      workMode: workModeDraft,
      postedWithin: postedWithinDraft,
    })
    setOpen(false)
  }

  function handleClearAndClose() {
    clearFilters()
    setOpen(false)
  }

  return (
    <DialogPrimitive.Root open={open} onOpenChange={handleOpenChange}>
      <DialogPrimitive.Trigger asChild>
        <button
          type="button"
          className="flex h-12 items-center gap-2 rounded-m3-button border border-m3-outline px-4 text-m3-label-lg text-m3-on-surface"
        >
          <SlidersHorizontal className="h-4 w-4" aria-hidden="true" />
          {activeFilterCount > 0 ? `Lọc (${activeFilterCount})` : 'Lọc'}
        </button>
      </DialogPrimitive.Trigger>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/30 data-open:animate-in data-open:fade-in-0 data-closed:animate-out data-closed:fade-out-0" />
        <DialogPrimitive.Content
          className="fixed inset-x-0 bottom-0 z-50 flex max-h-[85vh] flex-col rounded-t-m3-lg bg-m3-surface text-m3-on-surface shadow-m3-3 outline-none data-open:animate-in data-open:slide-in-from-bottom data-closed:animate-out data-closed:slide-out-to-bottom"
        >
          <div className="flex items-center justify-between border-b border-m3-outline-variant px-4 py-3">
            <DialogPrimitive.Title className="text-m3-title-md text-m3-on-surface">Lọc việc làm</DialogPrimitive.Title>
            <DialogPrimitive.Close asChild>
              <button
                type="button"
                aria-label="Đóng"
                className="flex h-10 w-10 items-center justify-center rounded-m3-xs text-m3-on-surface-variant focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary"
              >
                <X className="h-5 w-5" aria-hidden="true" />
              </button>
            </DialogPrimitive.Close>
          </div>

          <div className="flex-1 overflow-y-auto px-4 py-4">
            <div className="flex flex-col gap-4">
              <CategoryField value={categoryDraft} onChange={setCategoryDraft} catalogsQuery={catalogsQuery} />
              <LocationField value={locationDraft} onChange={setLocationDraft} catalogsQuery={catalogsQuery} />

              <div>
                <p className="text-m3-label-md text-m3-on-surface">Mức lương (triệu VNĐ)</p>
                <div className="mt-2 flex items-center gap-2">
                  <label className="flex h-12 flex-1 items-center gap-1 rounded-m3-xs border border-m3-outline bg-m3-surface px-2">
                    <span className="text-m3-body-sm text-m3-on-surface-variant">Từ</span>
                    <input
                      type="number"
                      min={0}
                      inputMode="numeric"
                      value={minDraft}
                      onChange={(event) => setMinDraft(event.target.value)}
                      aria-describedby={hasError ? salaryErrorId : undefined}
                      className="w-full bg-transparent text-right text-m3-body-md text-m3-on-surface outline-none"
                    />
                    <span className="text-m3-body-sm text-m3-on-surface-variant">triệu</span>
                  </label>
                  <label className="flex h-12 flex-1 items-center gap-1 rounded-m3-xs border border-m3-outline bg-m3-surface px-2">
                    <span className="text-m3-body-sm text-m3-on-surface-variant">Đến</span>
                    <input
                      type="number"
                      min={0}
                      inputMode="numeric"
                      value={maxDraft}
                      onChange={(event) => setMaxDraft(event.target.value)}
                      aria-describedby={hasError ? salaryErrorId : undefined}
                      className="w-full bg-transparent text-right text-m3-body-md text-m3-on-surface outline-none"
                    />
                    <span className="text-m3-body-sm text-m3-on-surface-variant">triệu</span>
                  </label>
                </div>
                {errorMessage && (
                  <p id={salaryErrorId} className="mt-2 text-m3-body-sm text-m3-error">
                    {errorMessage}
                  </p>
                )}
                <label className="mt-3 flex min-h-12 items-center gap-2 text-m3-body-md text-m3-on-surface">
                  <Checkbox
                    checked={hideUnlistedDraft}
                    onCheckedChange={(checked) => setHideUnlistedDraft(checked === true)}
                  />
                  Ẩn tin không công bố lương
                </label>
              </div>

              <div>
                <p className="text-m3-label-md text-m3-on-surface">Hình thức làm việc</p>
                <div className="mt-2 flex flex-col gap-1">
                  {WORK_MODE_OPTIONS.map((mode) => {
                    const checked = workModeDraft.includes(mode as WorkMode)
                    return (
                      <label
                        key={mode}
                        className={cn(
                          'flex min-h-12 cursor-pointer items-center gap-3 rounded-m3-xs px-2 text-m3-body-md text-m3-on-surface',
                          checked && 'bg-m3-primary/8',
                        )}
                      >
                        <Checkbox checked={checked} onCheckedChange={() => toggleWorkMode(mode as WorkMode)} />
                        {WORK_MODE_LABELS[mode]}
                      </label>
                    )
                  })}
                </div>
              </div>

              <div className="flex flex-col gap-1">
                <p className="text-m3-label-md text-m3-on-surface">Thời gian đăng</p>
                <select
                  value={postedWithinDraft ?? ''}
                  onChange={(event) =>
                    setPostedWithinDraft(event.target.value === '' ? null : (event.target.value as PostedWithin))
                  }
                  className="flex h-12 items-center gap-2 rounded-m3-xs border border-m3-outline bg-m3-surface px-3 text-m3-body-md text-m3-on-surface"
                >
                  <option value="">Mọi thời điểm</option>
                  {(Object.keys(POSTED_WITHIN_LABELS) as PostedWithin[]).map((key) => (
                    <option key={key} value={key}>
                      {POSTED_WITHIN_LABELS[key]}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3 border-t border-m3-outline-variant px-4 py-3">
            <button
              type="button"
              onClick={handleClearAndClose}
              className="flex h-12 flex-1 items-center justify-center rounded-m3-button border border-m3-outline text-m3-label-lg text-m3-on-surface"
            >
              Xoá bộ lọc
            </button>
            <button
              type="button"
              onClick={handleApply}
              disabled={hasError}
              className="flex h-12 flex-1 items-center justify-center rounded-m3-button bg-m3-primary text-m3-label-lg text-m3-on-primary disabled:cursor-not-allowed disabled:opacity-38"
            >
              Áp dụng
            </button>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}

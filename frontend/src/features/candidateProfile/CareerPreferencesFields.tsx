import { FileInput } from 'lucide-react'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { CatalogMultiCombobox } from '../catalog/CatalogMultiCombobox'
import type { Catalogs } from '../catalog/types'
import { WORK_MODE_LABELS, WORK_MODE_OPTIONS } from '../jobs/jobLabels'
import { SkillTagInput } from './SkillTagInput'

export const MAX_DESIRED_CATALOG_ITEMS = 3
export const MAX_SKILLS = 20
export const MAX_BIO_LENGTH = 500

// Gia tri cua nhom field dung chung giua /candidate/onboarding (dot 6) va card "Nghe nghiep va mong
// muon cong viec" cua /candidate/profile (dot 5, UI.md muc 5). La component "dumb" (value/onChange),
// khong phu thuoc truc tiep react-hook-form, de trang nao dung RHF cua rieng trang do cung ghep duoc.
export interface CareerPreferencesValues {
  headline: string
  desiredIndustryCodes: string[]
  desiredLocationCodes: string[]
  desiredWorkModes: string[]
  desiredSalaryMinMillions: string
  yearsExperience: string
  skills: string[]
  bio: string
}

export interface CareerPreferencesErrors {
  desiredSalaryMinMillions?: string
  bio?: string
}

interface CareerPreferencesFieldsProps {
  values: CareerPreferencesValues
  onFieldChange: <K extends keyof CareerPreferencesValues>(field: K, value: CareerPreferencesValues[K]) => void
  errors?: CareerPreferencesErrors
  catalogs: Catalogs | undefined
  catalogsLoading: boolean
  catalogsError: boolean
  onRetryCatalogs: () => void
  onAutofill: () => void
  autofillDisabled: boolean
  autofillDisabledReason: string
  autofillPending: boolean
  autofillErrorMessage?: string
  idPrefix: string
}

// Nhom field "Nghe nghiep va mong muon cong viec" (FR-U14 UI.md muc 4a/5). CHI render field, khong tu
// goi API/luu - trang cha (CandidateProfilePage, OnboardingPage o dot 6) nam toan bo logic submit va
// quy tac "Dien tu CV" R-A2 (vi R-A2 con dung toi field currentTitle o ngoai nhom nay).
export function CareerPreferencesFields({
  values,
  onFieldChange,
  errors,
  catalogs,
  catalogsLoading,
  catalogsError,
  onRetryCatalogs,
  onAutofill,
  autofillDisabled,
  autofillDisabledReason,
  autofillPending,
  autofillErrorMessage,
  idPrefix,
}: CareerPreferencesFieldsProps) {
  const headlineId = `${idPrefix}-headline`
  const industriesId = `${idPrefix}-industries`
  const industriesLabelId = `${industriesId}-label`
  const locationsId = `${idPrefix}-locations`
  const locationsLabelId = `${locationsId}-label`
  const workModeLabelId = `${idPrefix}-work-modes-label`
  const salaryId = `${idPrefix}-salary`
  const yearsId = `${idPrefix}-years`
  const skillsId = `${idPrefix}-skills`
  const bioId = `${idPrefix}-bio`
  const autofillCaptionId = `${idPrefix}-autofill-caption`
  const autofillErrorId = `${idPrefix}-autofill-error`

  function toggleWorkMode(mode: string) {
    const next = values.desiredWorkModes.includes(mode)
      ? values.desiredWorkModes.filter((item) => item !== mode)
      : [...values.desiredWorkModes, mode]
    onFieldChange('desiredWorkModes', next)
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1.5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Label htmlFor={headlineId} className="text-m3-on-surface">
            Chức danh mong muốn
          </Label>
          <button
            type="button"
            onClick={onAutofill}
            disabled={autofillDisabled || autofillPending}
            aria-describedby={autofillDisabled ? autofillCaptionId : undefined}
            className="flex h-10 items-center gap-1.5 rounded-m3-button border border-m3-outline-variant px-3 text-m3-label-lg text-m3-on-surface disabled:cursor-not-allowed disabled:opacity-38"
          >
            <FileInput className="h-4 w-4 shrink-0" aria-hidden="true" />
            {autofillPending ? 'Đang điền...' : 'Điền từ CV'}
          </button>
        </div>
        <input
          id={headlineId}
          type="text"
          placeholder="vd. Backend Developer"
          value={values.headline}
          onChange={(event) => onFieldChange('headline', event.target.value)}
          className="h-12 w-full rounded-m3-xs border border-m3-outline bg-m3-surface px-3 text-m3-body-md text-m3-on-surface outline-none placeholder:text-m3-on-surface-variant focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary sm:h-10"
        />
        {autofillDisabled && (
          <p id={autofillCaptionId} className="text-m3-body-sm text-m3-on-surface-variant">
            {autofillDisabledReason}
          </p>
        )}
        {autofillErrorMessage && (
          <p id={autofillErrorId} className="text-m3-body-sm text-m3-error">
            {autofillErrorMessage}
          </p>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label id={industriesLabelId} htmlFor={industriesId} className="text-m3-on-surface">
            {`Ngành nghề mong muốn (${values.desiredIndustryCodes.length}/${MAX_DESIRED_CATALOG_ITEMS})`}
          </Label>
          <CatalogMultiCombobox
            id={industriesId}
            labelId={industriesLabelId}
            value={values.desiredIndustryCodes}
            onChange={(codes) => onFieldChange('desiredIndustryCodes', codes)}
            items={catalogs?.industries}
            placeholder="Chọn ngành nghề"
            searchPlaceholder="Tìm ngành nghề..."
            isLoading={catalogsLoading}
            isError={catalogsError}
            onRetry={onRetryCatalogs}
            max={MAX_DESIRED_CATALOG_ITEMS}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label id={locationsLabelId} htmlFor={locationsId} className="text-m3-on-surface">
            {`Khu vực mong muốn (${values.desiredLocationCodes.length}/${MAX_DESIRED_CATALOG_ITEMS})`}
          </Label>
          <CatalogMultiCombobox
            id={locationsId}
            labelId={locationsLabelId}
            value={values.desiredLocationCodes}
            onChange={(codes) => onFieldChange('desiredLocationCodes', codes)}
            items={catalogs?.provinces}
            placeholder="Chọn khu vực"
            searchPlaceholder="Tìm khu vực..."
            isLoading={catalogsLoading}
            isError={catalogsError}
            onRetry={onRetryCatalogs}
            max={MAX_DESIRED_CATALOG_ITEMS}
          />
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1.5">
          <Label id={workModeLabelId} className="text-m3-on-surface">
            Hình thức làm việc mong muốn
          </Label>
          <div role="group" aria-labelledby={workModeLabelId} className="flex flex-wrap gap-x-4 gap-y-2">
            {WORK_MODE_OPTIONS.map((mode) => {
              const checked = values.desiredWorkModes.includes(mode)
              return (
                <label
                  key={mode}
                  className="flex min-h-12 cursor-pointer items-center gap-2 text-m3-body-md text-m3-on-surface sm:min-h-10"
                >
                  <Checkbox checked={checked} onCheckedChange={() => toggleWorkMode(mode)} />
                  {WORK_MODE_LABELS[mode]}
                </label>
              )
            })}
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor={salaryId} className="text-m3-on-surface">
            Mức lương mong muốn (triệu VNĐ)
          </Label>
          <label
            htmlFor={salaryId}
            className="flex h-12 items-center gap-2 rounded-m3-xs border border-m3-outline bg-m3-surface px-3 sm:h-10"
          >
            <input
              id={salaryId}
              type="number"
              min={0}
              max={1000}
              inputMode="numeric"
              value={values.desiredSalaryMinMillions}
              onChange={(event) => onFieldChange('desiredSalaryMinMillions', event.target.value)}
              aria-describedby={errors?.desiredSalaryMinMillions ? `${salaryId}-error` : undefined}
              className="w-full bg-transparent text-m3-body-md text-m3-on-surface outline-none"
            />
            <span className="shrink-0 text-m3-body-sm text-m3-on-surface-variant">trở lên</span>
          </label>
          {errors?.desiredSalaryMinMillions && (
            <p id={`${salaryId}-error`} className="text-m3-body-sm text-m3-error">
              {errors.desiredSalaryMinMillions}
            </p>
          )}
        </div>
      </div>

      <div className="flex flex-col gap-1.5 sm:w-1/2 sm:pr-2">
        <Label htmlFor={yearsId} className="text-m3-on-surface">
          Số năm kinh nghiệm
        </Label>
        <label
          htmlFor={yearsId}
          className="flex h-12 items-center gap-2 rounded-m3-xs border border-m3-outline bg-m3-surface px-3 sm:h-10"
        >
          <input
            id={yearsId}
            type="number"
            min={0}
            step={0.5}
            inputMode="decimal"
            value={values.yearsExperience}
            onChange={(event) => onFieldChange('yearsExperience', event.target.value)}
            className="w-full bg-transparent text-m3-body-md text-m3-on-surface outline-none"
          />
          <span className="shrink-0 text-m3-body-sm text-m3-on-surface-variant">năm</span>
        </label>
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor={skillsId} className="text-m3-on-surface">
          {`Kỹ năng chính (${values.skills.length}/${MAX_SKILLS})`}
        </Label>
        <SkillTagInput
          id={skillsId}
          value={values.skills}
          onChange={(skills) => onFieldChange('skills', skills)}
          max={MAX_SKILLS}
          placeholder="Nhập kỹ năng, Enter hoặc dấu phẩy để thêm"
          maxReachedPlaceholder="Đã đạt tối đa 20 kỹ năng."
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <div className="flex items-center justify-between gap-2">
          <Label htmlFor={bioId} className="text-m3-on-surface">
            Giới thiệu ngắn
          </Label>
          <span aria-live="polite" className="text-m3-body-sm text-m3-on-surface-variant">
            {`${values.bio.length}/${MAX_BIO_LENGTH}`}
          </span>
        </div>
        <textarea
          id={bioId}
          rows={3}
          maxLength={MAX_BIO_LENGTH}
          value={values.bio}
          onChange={(event) => onFieldChange('bio', event.target.value)}
          aria-describedby={errors?.bio ? `${bioId}-error` : undefined}
          className="w-full rounded-m3-xs border border-m3-outline bg-m3-surface px-3 py-2 text-m3-body-md text-m3-on-surface outline-none placeholder:text-m3-on-surface-variant focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary"
        />
        {errors?.bio && (
          <p id={`${bioId}-error`} className="text-m3-body-sm text-m3-error">
            {errors.bio}
          </p>
        )}
      </div>
    </div>
  )
}

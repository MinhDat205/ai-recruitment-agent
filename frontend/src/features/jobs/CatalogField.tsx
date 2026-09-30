import { Label } from '@/components/ui/label'
import { CatalogCombobox } from '../catalog/CatalogCombobox'
import type { CatalogItem } from '../catalog/types'
import { UnnormalizedBadge } from './UnnormalizedBadge'

interface CatalogFieldProps {
  id: string
  label: string
  helperText: string
  placeholder: string
  searchPlaceholder: string
  value: string | null
  onChange: (code: string | null) => void
  items: CatalogItem[] | undefined
  isLoading: boolean
  isError: boolean
  onRetry: () => void
  // Gia tri cu nguyen van cua Job "chua chuan hoa" (legacyCategory/legacyLocation tu backend).
  legacyValue?: string | null
}

// Mot o danh muc trong form tao/sua Job (FR-C05 UI.md muc 4a). Job chua chuan hoa: hien gia tri cu
// nguyen van kem nhan "Chua chuan hoa" cho toi khi HR chon ma - KHONG tu chon ma "doan" tu gia tri cu,
// khong tu xoa gia tri cu (UI.md muc 10).
export function CatalogField({
  id,
  label,
  helperText,
  placeholder,
  searchPlaceholder,
  value,
  onChange,
  items,
  isLoading,
  isError,
  onRetry,
  legacyValue,
}: CatalogFieldProps) {
  const labelId = `${id}-label`
  const helperId = `${id}-helper`
  const legacyId = `${id}-legacy`
  const showLegacy = value == null && Boolean(legacyValue)

  return (
    <div className="flex flex-col gap-1.5">
      <Label id={labelId} htmlFor={id}>
        {label}
      </Label>
      <CatalogCombobox
        id={id}
        labelId={labelId}
        value={value}
        onChange={onChange}
        items={items}
        placeholder={placeholder}
        searchPlaceholder={searchPlaceholder}
        isLoading={isLoading}
        isError={isError}
        onRetry={onRetry}
        describedBy={showLegacy ? `${helperId} ${legacyId}` : helperId}
      />
      <p id={helperId} className="text-m3-body-sm text-m3-on-surface-variant">
        {helperText}
      </p>
      {showLegacy && (
        <div id={legacyId} className="flex flex-col gap-1 text-m3-body-sm text-m3-on-surface">
          <span className="inline-flex flex-wrap items-center gap-2">
            <UnnormalizedBadge />
            <span>Giá trị cũ: &quot;{legacyValue}&quot;</span>
          </span>
          <span>Chọn lại từ danh mục trước khi mở tin.</span>
        </div>
      )}
    </div>
  )
}

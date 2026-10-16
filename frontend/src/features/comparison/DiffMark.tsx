import { COMPARE_ROW_TEXT } from './comparisonLabels'

// R-W2 - chip CHU "Khac nhau" o tieu de hang co co valuesDiffer (backend tinh). Khong icon, khong mau: thong tin nam o
// chu (duoc doc), nen cua hang chi la tin hieu phu (UI.md muc 9). Chi dat o tieu de hang, khong bao gio o mot o.
export function DiffMark() {
  return (
    <span className="inline-flex w-fit items-center rounded-m3-xs border border-m3-outline bg-m3-surface px-1.5 py-0.5 text-m3-label-md text-m3-on-surface">
      {COMPARE_ROW_TEXT.diff}
    </span>
  )
}

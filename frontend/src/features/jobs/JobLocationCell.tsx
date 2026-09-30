import { isLocationUnnormalized, jobLocationText } from './catalogDisplay'
import type { JobOwnerResponse } from './ownerTypes'
import { UnnormalizedBadge } from './UnnormalizedBadge'

// Cot Dia diem o danh sach tin cua HR (FR-C05 UI.md muc 4c): nhan cua ma / gia tri cu kem nhan
// "Chua chuan hoa" / "Lam tu xa" / "Chua co du lieu" (khong an, khong dung dau gach).
export function JobLocationCell({ job }: { job: JobOwnerResponse }) {
  const text = jobLocationText(job)
  if (!text) {
    return <span>Chưa có dữ liệu</span>
  }
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <span>{text}</span>
      {isLocationUnnormalized(job) && <UnnormalizedBadge />}
    </span>
  )
}

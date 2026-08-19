import { AlertCircle, CheckCircle2, Clock, Loader2 } from 'lucide-react'
import type { ScoringRunStatus } from './types'

interface ScoringRunStatusBadgeProps {
  status: ScoringRunStatus
  finishedAt: string | null
  criteriaScored: number
  criteriaTotal: number
}

// Man hinh nay CAM tuyet doi: nhan dat/khong dat, mau do/vang/xanh theo diem, cau goi y hanh dong
// ("nen moi phong van"...). Chi hien trang thai/tien do XU LY (dang cho, dang cham, da cham xong
// cho D3 tong hop, hay that bai ky thuat) - KHONG hien diem so hay evidence (D3/D4 moi co). Toan bo
// mau deu trung tinh (bg-canvas/text-ink-muted hoac bg-brand-light/text-brand mang y nghia "co
// thong tin", khong phai "tot") - giong dung tinh than ParseStatusBadge (features/resumes).
function labelAndIcon(status: ScoringRunStatus, finishedAt: string | null, criteriaScored: number, criteriaTotal: number) {
  if (status === 'PENDING') {
    return { label: 'Đang chờ xử lý', icon: Clock, spin: false, tone: 'muted' as const }
  }
  if (status === 'RUNNING' && finishedAt === null) {
    return {
      label: `Đang chấm — Đã chấm ${criteriaScored}/${criteriaTotal} tiêu chí`,
      icon: Loader2,
      spin: true,
      tone: 'muted' as const,
    }
  }
  if (status === 'RUNNING' && finishedAt !== null) {
    return { label: 'Đã chấm xong, đang chờ tổng hợp', icon: CheckCircle2, spin: false, tone: 'brand' as const }
  }
  if (status === 'FAILED') {
    return { label: 'Chấm điểm thất bại', icon: AlertCircle, spin: false, tone: 'muted' as const }
  }
  // status === 'DONE': D3 (FR-H05) moi tao ra trang thai nay, D2 khong bao gio set - giu nhan trung
  // tinh de khong vo bien khi D3 duoc them vao sau, khong thiet ke sau hon o day.
  return { label: 'Đã hoàn tất', icon: CheckCircle2, spin: false, tone: 'brand' as const }
}

const TONE_STYLES = {
  muted: 'bg-canvas text-ink-muted',
  brand: 'bg-brand-light text-brand',
}

export function ScoringRunStatusBadge({ status, finishedAt, criteriaScored, criteriaTotal }: ScoringRunStatusBadgeProps) {
  const { label, icon: Icon, spin, tone } = labelAndIcon(status, finishedAt, criteriaScored, criteriaTotal)
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-(--radius-badge) px-2 py-1 text-xs font-medium ${TONE_STYLES[tone]}`}
    >
      <Icon className={`h-3 w-3 ${spin ? 'animate-spin' : ''}`} aria-hidden="true" />
      {label}
    </span>
  )
}

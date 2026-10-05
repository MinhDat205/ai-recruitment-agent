import { Building2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { formatDeadline } from '@/lib/date'
import { jobCategoryText, jobLocationText } from './catalogDisplay'
import { UnnormalizedBadge } from './UnnormalizedBadge'
import type { JobSummary } from './types'

function formatSalary(job: JobSummary): string | null {
  if (job.salaryMin == null && job.salaryMax == null) {
    return null
  }
  const currency = job.salaryCurrency ?? 'VND'
  const format = (value: number) => value.toLocaleString('vi-VN')
  if (job.salaryMin != null && job.salaryMax != null) {
    return `${format(job.salaryMin)} - ${format(job.salaryMax)} ${currency}`
  }
  const value = job.salaryMin ?? job.salaryMax
  return value != null ? `${format(value)} ${currency}` : null
}

// R-S5: tien te khac VND, so sanh khong phan biet hoa/thuong va khoang trang (cung quy tac
// JobPublicService dung o backend). Chi co nghia khi tin CO luong - Thoa thuan khong co gi de so.
function isForeignCurrency(job: JobSummary): boolean {
  if (job.salaryMin == null && job.salaryMax == null) return false
  if (!job.salaryCurrency) return false
  return job.salaryCurrency.trim().toUpperCase() !== 'VND'
}

// R-N4 ngoai le (dac ta duyet bo sung dot 4): tin work_mode=REMOTE khong co location_code VA
// khong co gia tri cu KHONG phai du lieu thieu - C05 R-J3 cho phep REMOTE bo qua location_code khi
// mo tin (docs/features/CHUNG/C05/REQUIREMENT.md:173-174), R-J7 hien "Lam tu xa" cho truong hop nay
// (:194-195) - khac tin ONSITE/HYBRID thieu tinh/thanh (that su thieu du lieu). Khong hien nhan
// "Chua chuan hoa" cho truong hop nay. REMOTE con gia tri cu (R-N3) van hien nhan binh thuong - do
// la chua chuan hoa THAT (gia tri cu chua duoc gan ma), khong lien quan gi REMOTE.
function shouldShowLocationUnnormalizedBadge(job: JobSummary): boolean {
  if (job.locationCode != null) return false
  if (job.workMode === 'REMOTE' && job.legacyLocation == null) return false
  return true
}

// Cac dieu kien loc dang ap dung o thanh loc (FR-U07) - de JobCard biet co nen hien nhan "Chua
// chuan hoa" (R-N3/R-N4)/chu thich ngoai te (R-S5) hay khong. Mac dinh "khong loc gi" de cac noi
// goi JobCard khac (RecommendedJobs - khong co thanh loc) giu dung hanh vi cu, khong doi.
export interface JobCardFilterContext {
  isCategoryFilterActive: boolean
  isLocationFilterActive: boolean
  isSalaryFilterActive: boolean
}

const NO_FILTER_CONTEXT: JobCardFilterContext = {
  isCategoryFilterActive: false,
  isLocationFilterActive: false,
  isSalaryFilterActive: false,
}

interface JobCardProps {
  job: JobSummary
  filterContext?: JobCardFilterContext
  // FR-U15 R-M1 - tuy chon, chi khoi goi y (RecommendedJobs) truyen vao. Noi khac dung JobCard
  // KHONG truyen prop nay -> giao dien khong doi (R-M, UI.md muc 5).
  matchedConditions?: string[]
}

// UI.md muc 4c/4d (R-L1, dieu chinh sau soat tay 02/10/2026): mau luong text-m3-tertiary, han nop
// len goc phai tren cung hang tieu de, o logo trong luon hien icon Building2 (bo nhanh chu viet tat
// - ten cong ty nao cung bat dau bang "Cong ty" nen chu viet tat vo nghia). Nhan "Chua chuan
// hoa"/chu thich ngoai te CHI hien dung luc dang loc truong tuong ung (R-N3/R-N4/R-S5) - khong hien
// tran lan khi khong loc (UI.md muc 10).
export function JobCard({ job, filterContext = NO_FILTER_CONTEXT, matchedConditions }: JobCardProps) {
  const salary = formatSalary(job)
  const locationText = jobLocationText(job)
  const categoryText = jobCategoryText(job)
  const showForeignCurrencyNote = filterContext.isSalaryFilterActive && isForeignCurrency(job)

  return (
    <Link
      to={`/jobs/${job.id}`}
      className="flex gap-4 rounded-(--radius-card) border border-line bg-surface p-4 transition hover:border-brand hover:shadow-sm"
    >
      <div className="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-(--radius-badge) border border-line bg-m3-surface-container">
        {job.company?.logoUrl ? (
          <img src={job.company.logoUrl} alt={job.company.name} className="h-full w-full object-cover" />
        ) : (
          <Building2 className="h-7 w-7 text-m3-on-surface-variant" aria-hidden="true" />
        )}
      </div>

      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex items-start justify-between gap-3">
          <h3 className="line-clamp-2 min-w-0 text-base font-medium text-ink hover:text-brand">{job.title}</h3>
          <span className="shrink-0 whitespace-nowrap text-xs text-ink-muted">
            Hạn nộp: {formatDeadline(job.deadline)}
          </span>
        </div>
        {job.company && <p className="text-sm text-ink-muted">{job.company.name}</p>}

        {salary && (
          <p className="text-sm font-medium text-m3-tertiary">
            {salary}
            {showForeignCurrencyNote && (
              <span className="ml-1 text-xs font-normal text-m3-on-surface-variant">
                (không áp dụng bộ lọc lương)
              </span>
            )}
          </p>
        )}

        {/* FR-C05 R-J7: nhan cua ma -> gia tri cu -> "Lam tu xa". Thieu han thi AN chip (ngoai le da
            duyet, UI.md muc 4d); khong hien nhan "Chua chuan hoa" o day - xem UnnormalizedField
            ben duoi, chi hien dung luc dang loc (R-N3/R-N4). */}
        <div className="mt-1 flex flex-wrap gap-2">
          {locationText && (
            <span className="rounded-(--radius-badge) bg-brand-light px-3 py-1 text-xs text-brand">
              {locationText}
            </span>
          )}
          {categoryText && (
            <span className="rounded-(--radius-badge) bg-brand-light px-3 py-1 text-xs text-brand">
              {categoryText}
            </span>
          )}
        </div>

        {filterContext.isCategoryFilterActive && job.categoryCode == null && (
          <UnnormalizedField legacyValue={job.legacyCategory} />
        )}
        {filterContext.isLocationFilterActive && shouldShowLocationUnnormalizedBadge(job) && (
          <UnnormalizedField legacyValue={job.legacyLocation} />
        )}

        {/* FR-U15 R-M (sua sau soat tay 03/10/2026) - MOT dong van ban duy nhat, tu xuong dong,
            khong con chip rieng tung dieu kien (tranh chong voi tag khu vuc/nganh phia tren va
            lap lai y nguyen nhan). Trung tinh mot mau duy nhat, khong %, khong dau check. aria-label
            gop giu nguyen y nghia cu (UI_GUIDE muc 9) - noi dung hien thi va aria-label giong nhau
            nen khong can tach aria-hidden nhu truoc. */}
        {matchedConditions && matchedConditions.length > 0 && (
          <p
            className="mt-1 rounded-(--radius-badge) bg-m3-surface-container px-3 py-1 text-xs text-m3-on-surface"
            aria-label={`Điều kiện khớp: ${matchedConditions.join(', ')}`}
          >
            <span className="font-medium">Khớp mong muốn:</span> {matchedConditions.join(' · ')}
          </p>
        )}
      </div>
    </Link>
  )
}

// R-N3 (con gia tri cu): nhan + dong "Gia tri cu: ...". R-N4 (thieu han, khong co gi de hien): chi
// nhan, khong co dong gia tri cu.
function UnnormalizedField({ legacyValue }: { legacyValue: string | null }) {
  return (
    <div className="flex flex-wrap items-center gap-2 text-xs text-ink">
      <UnnormalizedBadge />
      {legacyValue && <span>Giá trị cũ: &quot;{legacyValue}&quot;</span>}
    </div>
  )
}

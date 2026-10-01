import type { ReactNode } from 'react'
import type { ResumeExperienceSummary, ResumeParsedDataResponse } from './types'

const MISSING_TEXT = 'Chưa có dữ liệu'

// "3.5" -> "3,5"; so tu backend da co dung 1 chu so thap phan (R-E8). toFixed(1) chi khoi phuc so 0 cuoi
// ma JSON.parse lam mat (2.0 -> 2), KHONG lam tron lai; frontend khong tu chia thang -> nam.
function formatYears(years: number): string {
  return years.toFixed(1).replace('.', ',')
}

// "2026-09" -> "09/2026".
function formatReferenceMonth(referenceMonth: string): string {
  const [year, month] = referenceMonth.split('-')
  return `${month}/${year}`
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 sm:grid sm:grid-cols-[10rem_1fr] sm:gap-4">
      <dt className="text-m3-body-sm text-m3-on-surface-variant">{label}</dt>
      <dd className="text-m3-body-md text-m3-on-surface">{children}</dd>
    </div>
  )
}

function Missing() {
  return <span className="text-m3-on-surface-variant">{MISSING_TEXT}</span>
}

function ExperienceValue({ experience }: { experience: ResumeExperienceSummary | null }) {
  if (!experience) {
    return <span className="text-m3-on-surface-variant">Đang tính…</span>
  }
  if (experience.months == null || experience.years == null) {
    return (
      <div className="flex flex-col gap-0.5">
        <Missing />
        {experience.skippedEntries > 0 && (
          <span className="text-m3-body-sm text-m3-on-surface-variant">
            Không đọc được mốc thời gian ở {experience.skippedEntries} mục kinh nghiệm.
          </span>
        )}
      </div>
    )
  }
  const total = experience.countedEntries + experience.skippedEntries
  return (
    <div className="flex flex-col gap-0.5">
      <span>{formatYears(experience.years)} năm</span>
      <span className="text-m3-body-sm text-m3-on-surface-variant">
        Tính trên {experience.countedEntries}/{total} mục kinh nghiệm, tính đến tháng{' '}
        {formatReferenceMonth(experience.referenceMonth)}.
      </span>
    </div>
  )
}

function LocationValue({ data }: { data: ResumeParsedDataResponse }) {
  if (data.location) {
    return <>{data.location.label}</>
  }
  if (data.locationText) {
    return <>{data.locationText} (chưa khớp danh mục)</>
  }
  return <Missing />
}

// Muc "Tong quan nghe nghiep" dau dialog du lieu da trich xuat (FR-C05 UI.md muc 4f, 6, 7). Moi dong luon
// hien (thieu thi "Chua co du lieu"), khong nhan "Do AI tao", khong mau theo muc, khong thanh/thang mau cho
// kinh nghiem (UI.md muc 5, 10). Nganh/khu vuc lay nhan backend tra theo ma da qua kiem.
export function CareerOverviewSection({ data }: { data: ResumeParsedDataResponse }) {
  return (
    <section
      aria-labelledby="career-overview-title"
      className="flex flex-col gap-2 border-b border-m3-outline-variant pb-4"
    >
      <h3 id="career-overview-title" className="text-m3-label-lg text-m3-on-surface">
        Tổng quan nghề nghiệp
      </h3>
      {data.schemaVersion === 1 && (
        <p className="text-m3-body-sm text-m3-on-surface-variant">
          Dữ liệu trích xuất theo phiên bản cũ — cập nhật ở danh sách CV để có đủ các mục dưới đây.
        </p>
      )}
      <dl className="flex flex-col gap-2">
        <Row label="Chức danh hiện tại">{data.currentTitle ?? <Missing />}</Row>
        <Row label="Ngành nghề">{data.industry?.label ?? <Missing />}</Row>
        <Row label="Khu vực">
          <LocationValue data={data} />
        </Row>
        <Row label="Kinh nghiệm">
          <ExperienceValue experience={data.experience} />
        </Row>
      </dl>
    </section>
  )
}

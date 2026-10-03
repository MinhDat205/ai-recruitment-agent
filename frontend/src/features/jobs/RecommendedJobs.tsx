import { Link } from 'react-router-dom'
import { useMyProfileQuery } from '../candidateProfile/queries'
import { JobCard } from './JobCard'
import { JobCardSkeleton } from './JobCardSkeleton'
import { useJobRecommendationsQuery } from './queries'
import type { JobRecommendationSource } from './types'

const SKELETON_KEYS = ['a', 'b', 'c']

// FR-U15 R-S2 - dong "Dua tren..." doc dung theo source tra ve tu backend, khong tu doan.
const SOURCE_LABELS: Record<JobRecommendationSource, string> = {
  CV: 'Dựa trên CV chính của bạn',
  PROFILE: 'Dựa trên hồ sơ nghề nghiệp của bạn',
  DESIRES: 'Dựa trên ngành nghề và khu vực bạn chọn',
}

// R-X1/R-X2 - chi categoryCode/locationCode (lap tham so, KHONG co salaryMin/salaryMax/workMode).
function buildViewAllHref(categoryCodes: string[], locationCodes: string[]): string {
  const params = new URLSearchParams()
  for (const code of categoryCodes) {
    params.append('categoryCode', code)
  }
  for (const code of locationCodes) {
    params.append('locationCode', code)
  }
  const query = params.toString()
  return query ? `/candidate?${query}` : '/candidate'
}

interface RecommendedJobsProps {
  // R-X3 - true o /candidate/dashboard (LUON hien nut "Xem tat ca"); false (mac dinh) o /candidate
  // (AN nut khi ung vien khong khai ca nganh lan khu vuc). Nhan qua prop de biet dang o trang nao,
  // KHONG tu doan tu URL hien tai.
  alwaysShowViewAll?: boolean
}

// FR-U15 - viet lai hoan toan (truoc day tu suy trang thai rong tu useResumesQuery/parseStatus,
// F1/FR-U04). Backend tra status/source tuong minh (R-S1) - component chi doc va hien dung theo
// do, KHONG tu doan tu truong nao khac.
export function RecommendedJobs({ alwaysShowViewAll = false }: RecommendedJobsProps) {
  const { data, isLoading, isError, refetch } = useJobRecommendationsQuery()
  const { data: profile } = useMyProfileQuery()

  const desiredIndustryCodes = profile?.desiredIndustries.map((item) => item.code) ?? []
  const desiredLocationCodes = profile?.desiredLocations.map((item) => item.code) ?? []
  const hasDesires = desiredIndustryCodes.length > 0 || desiredLocationCodes.length > 0
  const showViewAll = alwaysShowViewAll || hasDesires
  const viewAllHref = buildViewAllHref(desiredIndustryCodes, desiredLocationCodes)

  const status = data?.status
  const source = data?.source ?? null
  const showSourceLine = source != null && (status === 'READY' || status === 'NO_RESULT' || status === 'PREPARING')

  return (
    <section aria-label="Gợi ý việc làm cho bạn">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-lg font-semibold text-ink">Gợi ý cho bạn</h2>
        {showViewAll && (
          // index.css dong 124 dat "a { color: inherit }" KHONG boc trong @layer (unlayered) - CSS
          // cascade layers cho unlayered LUON thang layer "utilities" cua Tailwind du specificity
          // thap hon, nen text-m3-primary/hover:underline dat TRUC TIEP tren <Link> (render ra <a>)
          // bi de thanh mau ke thua (den) - phai dat hai class do tren <span> con, KHONG phai tren
          // <a> (xem bao cao Dot 9b, da build CSS thuc kiem chung). focus-visible van dat tren Link
          // (nhan focus) - khong bi anh huong vi khong trung thuoc tinh voi rule unlayered o tren.
          <Link
            to={viewAllHref}
            className="shrink-0 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary"
          >
            <span className="text-sm font-medium text-m3-primary hover:underline">Xem tất cả</span>
          </Link>
        )}
      </div>

      {showSourceLine && source && (
        <p className="mt-1 text-sm text-ink-muted">{SOURCE_LABELS[source]}</p>
      )}

      <div className="mt-4">
        {isLoading && (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            {SKELETON_KEYS.map((key) => (
              <JobCardSkeleton key={key} />
            ))}
          </div>
        )}

        {!isLoading && isError && (
          <div className="flex flex-col items-center gap-3 py-10 text-center">
            <p className="text-sm text-ink-muted">Không tải được gợi ý việc làm.</p>
            <button
              type="button"
              onClick={() => refetch()}
              className="h-10 rounded-md border border-brand px-5 text-sm font-medium text-brand"
            >
              Thử lại
            </button>
          </div>
        )}

        {!isLoading && !isError && status === 'NO_DATA' && (
          <div aria-live="polite" className="flex flex-col items-center gap-2 py-10 text-center">
            <p className="text-sm text-ink-muted">
              Hãy hoàn thiện hồ sơ nghề nghiệp hoặc tải CV để nhận gợi ý việc làm phù hợp.
            </p>
            {/* Mau/gach chan dat tren <span> con, khong tren <a> - xem comment o nut "Xem tat ca" */}
            <Link
              to="/candidate/profile"
              className="focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary"
            >
              <span className="text-sm text-m3-primary hover:underline">Hoàn thiện hồ sơ</span>
            </Link>
          </div>
        )}

        {!isLoading && !isError && status === 'PREPARING' && (
          <div aria-live="polite" className="flex flex-col items-center gap-2 py-10 text-center">
            <p className="text-sm text-ink-muted">
              {source === 'PROFILE'
                ? 'Hệ thống đang phân tích hồ sơ của bạn. Gợi ý sẽ xuất hiện sau khi phân tích hoàn tất.'
                : 'Hệ thống đang phân tích CV của bạn. Gợi ý sẽ xuất hiện sau khi phân tích hoàn tất.'}
            </p>
          </div>
        )}

        {!isLoading && !isError && status === 'NO_RESULT' && (
          <div aria-live="polite" className="flex flex-col items-center gap-2 py-10 text-center">
            <p className="text-sm text-ink-muted">
              Chưa có việc làm nào để gợi ý lúc này.
              {hasDesires && ' Hãy thử mở rộng ngành nghề hoặc khu vực mong muốn.'}
            </p>
            {/* Mau/gach chan dat tren <span> con, khong tren <a> - xem comment o nut "Xem tat ca" */}
            {hasDesires && (
              <Link
                to="/candidate/profile"
                className="focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary"
              >
                <span className="text-sm text-m3-primary hover:underline">Chỉnh mong muốn</span>
              </Link>
            )}
          </div>
        )}

        {!isLoading && !isError && status === 'READY' && data && (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            {data.items.map((item, index) => (
              // R-L1 - duoi sm chi hien 3 the dau (an bang CSS, khong giam so luong goi API/tra ve
              // tu backend); tu sm tro len hien du 6.
              <div key={item.job.id} className={index >= 3 ? 'hidden sm:block' : undefined}>
                <JobCard job={item.job} matchedConditions={item.matchedConditions} />
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}

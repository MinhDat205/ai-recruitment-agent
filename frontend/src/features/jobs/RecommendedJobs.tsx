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
        <h2 className="text-lg font-semibold text-m3-on-surface">Gợi ý cho bạn</h2>
        {showViewAll && (
          // Khoi nay nam thang tren nen trang (m3-surface-container) - m3-primary chi dat 4.07:1 tren
          // nen do, nen lien ket dung m3-on-primary-container (6.23:1).
          <Link
            to={viewAllHref}
            className="shrink-0 text-sm font-medium text-m3-on-primary-container hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary"
          >
            Xem tất cả
          </Link>
        )}
      </div>

      {showSourceLine && source && (
        <p className="mt-1 text-sm text-m3-on-surface">{SOURCE_LABELS[source]}</p>
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
            <p className="text-sm text-m3-on-surface">Không tải được gợi ý việc làm.</p>
            <button
              type="button"
              onClick={() => refetch()}
              className="h-10 rounded-md border border-m3-primary px-5 text-sm font-medium text-m3-on-primary-container"
            >
              Thử lại
            </button>
          </div>
        )}

        {!isLoading && !isError && status === 'NO_DATA' && (
          <div aria-live="polite" className="flex flex-col items-center gap-2 py-10 text-center">
            <p className="text-sm text-m3-on-surface">
              Hãy hoàn thiện hồ sơ nghề nghiệp hoặc tải CV để nhận gợi ý việc làm phù hợp.
            </p>
            <Link
              to="/candidate/profile"
              className="text-sm text-m3-on-primary-container hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary"
            >
              Hoàn thiện hồ sơ
            </Link>
          </div>
        )}

        {!isLoading && !isError && status === 'PREPARING' && (
          <div aria-live="polite" className="flex flex-col items-center gap-2 py-10 text-center">
            <p className="text-sm text-m3-on-surface">
              {source === 'PROFILE'
                ? 'Hệ thống đang phân tích hồ sơ của bạn. Gợi ý sẽ xuất hiện sau khi phân tích hoàn tất.'
                : 'Hệ thống đang phân tích CV của bạn. Gợi ý sẽ xuất hiện sau khi phân tích hoàn tất.'}
            </p>
          </div>
        )}

        {!isLoading && !isError && status === 'NO_RESULT' && (
          <div aria-live="polite" className="flex flex-col items-center gap-2 py-10 text-center">
            <p className="text-sm text-m3-on-surface">
              Chưa có việc làm nào để gợi ý lúc này.
              {hasDesires && ' Hãy thử mở rộng ngành nghề hoặc khu vực mong muốn.'}
            </p>
            {hasDesires && (
              <Link
                to="/candidate/profile"
                className="text-sm text-m3-on-primary-container hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary"
              >
                Chỉnh mong muốn
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

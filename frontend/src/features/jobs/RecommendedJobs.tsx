import { Sparkles } from 'lucide-react'
import { JobCard } from './JobCard'
import { JobCardSkeleton } from './JobCardSkeleton'
import { useJobRecommendationsQuery } from './queries'

const SKELETON_KEYS = ['a', 'b', 'c']

// 3 cot man rong (khac lg:grid-cols-2 cua JobList o trang tim kiem day du): day la mot khoi
// phu tren trang chu, can gon hon danh sach tim kiem chinh - dung thiet ke da chot o Plan Mode
// F1 muc H. 1 cot tren mobile.
export function RecommendedJobs() {
  const { data, isLoading, isError, refetch } = useJobRecommendationsQuery()

  return (
    <section className="mt-8">
      <h2 className="flex items-center gap-2 text-lg font-semibold text-ink">
        <Sparkles className="h-5 w-5 text-brand" aria-hidden="true" />
        Việc làm phù hợp với bạn
      </h2>

      <div className="mt-4">
        {isLoading && (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            {SKELETON_KEYS.map((key) => (
              <JobCardSkeleton key={key} />
            ))}
          </div>
        )}

        {isError && (
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

        {!isLoading && !isError && data && data.length === 0 && (
          <p className="py-10 text-center text-sm text-ink-muted">
            Chưa có gợi ý việc làm phù hợp. Hãy tải CV lên và đợi hệ thống phân tích để nhận gợi ý
            phù hợp với bạn.
          </p>
        )}

        {!isLoading && !isError && data && data.length > 0 && (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            {data.map((job) => (
              <JobCard key={job.id} job={job} />
            ))}
          </div>
        )}
      </div>
    </section>
  )
}

import { Link } from 'react-router-dom'
import { Sparkles } from 'lucide-react'
import { JobCard } from './JobCard'
import { JobCardSkeleton } from './JobCardSkeleton'
import { useJobRecommendationsQuery } from './queries'
import { useResumesQuery } from '../resumes/queries'
import type { Resume } from '../resumes/types'

const SKELETON_KEYS = ['a', 'b', 'c']

type EmptyReason = 'NO_RESUME' | 'PARSING' | 'FAILED' | 'NO_MATCH' | 'UNKNOWN'

// Chi CV chinh (isPrimary) anh huong goi y: JobRecommendationCacheService (backend) chi doc
// embedding cua resume co isPrimary=true khi tinh cache, CV phu khong duoc dung. resumes undefined
// nghia la danh sach CV chua tai xong HOAC tai loi (API /candidates/resumes that bai) - hai truong
// hop nay KHONG the gop vao NO_RESUME: ung vien co the da co CV, chi la API dang loi, gop vao se
// hien nham "chua tai CV" cho nguoi da co CV. Tach rieng UNKNOWN, thong bao trung lap khong khang
// dinh gi ve trang thai CV. resumes rong (API thanh cong, tra ve mang rong) moi la NO_RESUME that.
function resolveEmptyReason(resumes: Resume[] | undefined): EmptyReason {
  if (!resumes) {
    return 'UNKNOWN'
  }
  if (resumes.length === 0) {
    return 'NO_RESUME'
  }
  const primary = resumes.find((resume) => resume.isPrimary) ?? resumes[0]
  if (primary.parseStatus === 'PENDING' || primary.parseStatus === 'PROCESSING') {
    return 'PARSING'
  }
  if (primary.parseStatus === 'FAILED') {
    return 'FAILED'
  }
  return 'NO_MATCH'
}

const EMPTY_STATE_MESSAGE: Record<EmptyReason, string> = {
  NO_RESUME: 'Bạn chưa tải CV lên. Hãy tải CV để hệ thống phân tích và gợi ý việc làm phù hợp.',
  PARSING:
    'Hệ thống đang phân tích CV của bạn. Gợi ý việc làm sẽ xuất hiện sau khi phân tích hoàn tất.',
  FAILED: 'Không phân tích được CV của bạn. Vui lòng thử phân tích lại hoặc tải lên bản khác.',
  NO_MATCH:
    'Hiện chưa có vị trí tuyển dụng nào đủ phù hợp với hồ sơ của bạn. Hệ thống chỉ gợi ý những vị trí đạt ngưỡng tương đồng tối thiểu, thay vì hiển thị mọi tin tuyển dụng đang mở.',
  UNKNOWN: 'Chưa có gợi ý việc làm phù hợp với bạn.',
}

// Chi hien link sang Ho so & CV khi buoc tiep theo cua ung vien la o do (tai CV moi/thu phan tich
// lai) - PARSING va NO_MATCH khong can hanh dong gi them tu ung vien.
function showsProfileLink(reason: EmptyReason): boolean {
  return reason === 'NO_RESUME' || reason === 'FAILED'
}

// 3 cot man rong (khac lg:grid-cols-2 cua JobList o trang tim kiem day du): day la mot khoi
// phu tren trang chu, can gon hon danh sach tim kiem chinh - dung thiet ke da chot o Plan Mode
// F1 muc H. 1 cot tren mobile.
export function RecommendedJobs() {
  const { data, isLoading, isError, refetch } = useJobRecommendationsQuery()
  const { data: resumes, isLoading: resumesLoading } = useResumesQuery()

  const showLoading = isLoading || resumesLoading
  const showEmpty = !showLoading && !isError && data && data.length === 0
  const emptyReason = resolveEmptyReason(resumes)

  return (
    <section>
      <div className="flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-ink">
          <Sparkles className="h-5 w-5 text-brand" aria-hidden="true" />
          Việc làm phù hợp với bạn
        </h2>
        {/* Tro ve "/" (danh sach cong khai, MOI job dang mo) - CO CHU Y khong dat ten "Xem tat
            ca" vi day KHONG PHAI trang xem day du goi y: /candidates/job-recommendations da tra
            ve toan bo goi y roi (toi da 10 dong, gioi han o JobRecommendationCacheService.TOP_N
            khi GHI cache, khong phai o luc doc/phan trang) - khong co "phan con lai" nao de xem
            them trong pham vi goi y. Nhan trung thuc voi dich that: tim job KHAC, khong phai xem
            THEM goi y. */}
        <Link to="/" className="shrink-0 text-sm font-medium text-brand hover:underline">
          Tìm thêm việc làm khác
        </Link>
      </div>

      <div className="mt-4">
        {showLoading && (
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

        {showEmpty && (
          <div className="flex flex-col items-center gap-2 py-10 text-center">
            <p className="text-sm text-ink-muted">{EMPTY_STATE_MESSAGE[emptyReason]}</p>
            {showsProfileLink(emptyReason) && (
              <Link to="/candidate/profile" className="text-sm text-brand hover:underline">
                Đi tới Hồ sơ &amp; CV
              </Link>
            )}
          </div>
        )}

        {!showLoading && !isError && data && data.length > 0 && (
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

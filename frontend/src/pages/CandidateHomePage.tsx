import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { ClipboardList, FileText, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { useMyApplicationsQuery } from '../features/applications/queries'
import { APPLICATION_STATUS_LABELS } from '../features/applications/applicationLabels'
import type { ApplicationStatus, ApplicationSummary } from '../features/applications/types'
import { useAuth } from '../features/auth/useAuth'
import { RecommendedJobs } from '../features/jobs/RecommendedJobs'
import { useJobRecommendationsQuery } from '../features/jobs/queries'
import { ParseStatusBadge } from '../features/resumes/ParseStatusBadge'
import { useResumesQuery } from '../features/resumes/queries'

// Thu tu hien thi phan bo trang thai don - theo dung trinh tu vong doi FR-U03 (PENDING ->
// INTERVIEW_INVITED -> HIRED | REJECTED, WITHDRAWN co the xay ra bat ky luc nao), khong phai thu
// tu bang chu cai.
const STATUS_ORDER: ApplicationStatus[] = ['PENDING', 'INTERVIEW_INVITED', 'HIRED', 'REJECTED', 'WITHDRAWN']

function lowerFirst(text: string): string {
  return text.length === 0 ? text : text.charAt(0).toLowerCase() + text.slice(1)
}

// Vd "2 chờ duyệt · 1 đã rút đơn" - chi liet ke trang thai co it nhat 1 don, theo dung thu tu
// STATUS_ORDER. APPLICATION_STATUS_LABELS viet hoa dau cau (dung cho badge dung mot minh), ha
// chu cai dau de ghep tu nhien sau so dem.
function summarizeApplicationStatuses(applications: ApplicationSummary[]): string {
  const counts = new Map<ApplicationStatus, number>()
  for (const application of applications) {
    counts.set(application.status, (counts.get(application.status) ?? 0) + 1)
  }
  return STATUS_ORDER.filter((status) => (counts.get(status) ?? 0) > 0)
    .map((status) => `${counts.get(status)} ${lowerFirst(APPLICATION_STATUS_LABELS[status])}`)
    .join(' · ')
}

function SummaryCard({ icon: Icon, title, children }: { icon: LucideIcon; title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-3 rounded-(--radius-card) bg-surface p-5">
      <div className="flex items-center gap-2 text-sm font-medium text-ink-muted">
        <Icon className="h-4 w-4 text-brand" aria-hidden="true" />
        {title}
      </div>
      {children}
    </div>
  )
}

export function CandidateHomePage() {
  const { user } = useAuth()
  const { data: resumes, isLoading: resumesLoading, isError: resumesError } = useResumesQuery()
  const {
    data: applications,
    isLoading: applicationsLoading,
    isError: applicationsError,
  } = useMyApplicationsQuery()
  const {
    data: recommendations,
    isLoading: recommendationsLoading,
    isError: recommendationsError,
  } = useJobRecommendationsQuery()

  const primaryResume = resumes?.find((resume) => resume.isPrimary)

  return (
    <CandidateLayout>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-4 py-8 md:px-6">
        <h1 className="text-2xl font-semibold text-ink">Xin chào {user?.fullName}</h1>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <SummaryCard icon={FileText} title="CV chính">
            {resumesLoading ? (
              <p className="text-sm text-ink-muted">Đang tải...</p>
            ) : resumesError ? (
              <p className="text-sm text-ink-muted">Không tải được thông tin CV.</p>
            ) : primaryResume ? (
              <div className="flex flex-col gap-2">
                <p className="truncate text-sm font-medium text-ink" title={primaryResume.fileName}>
                  {primaryResume.fileName}
                </p>
                <ParseStatusBadge status={primaryResume.parseStatus} />
              </div>
            ) : (
              <div className="flex flex-col gap-2">
                <p className="text-sm text-ink-muted">Bạn chưa tải CV lên.</p>
                <Link to="/candidate/profile" className="text-sm font-medium text-brand hover:underline">
                  Tải CV lên
                </Link>
              </div>
            )}
          </SummaryCard>

          <SummaryCard icon={ClipboardList} title="Đơn ứng tuyển">
            {applicationsLoading ? (
              <p className="text-sm text-ink-muted">Đang tải...</p>
            ) : applicationsError ? (
              <p className="text-sm text-ink-muted">Không tải được danh sách đơn.</p>
            ) : (
              <div className="flex flex-col gap-1">
                <p className="text-2xl font-semibold text-ink">{applications?.length ?? 0}</p>
                <p className="text-sm text-ink-muted">
                  {applications && applications.length > 0
                    ? summarizeApplicationStatuses(applications)
                    : 'Bạn chưa ứng tuyển vị trí nào.'}
                </p>
              </div>
            )}
          </SummaryCard>

          <SummaryCard icon={Sparkles} title="Gợi ý phù hợp">
            {recommendationsLoading || resumesLoading ? (
              <p className="text-sm text-ink-muted">Đang tải...</p>
            ) : recommendationsError ? (
              <p className="text-sm text-ink-muted">Không tải được gợi ý việc làm.</p>
            ) : (
              <div className="flex flex-col gap-1">
                {/* FR-U15 - sua toi thieu de khop kieu tra ve moi ({status,source,items} thay cho
                    mang truoc day) - KHONG doi logic/chu hien thi khac cua the nay (ngoai pham vi
                    dot 7, chi thay khoi RecommendedJobs ben duoi). */}
                <p className="text-2xl font-semibold text-ink">{recommendations?.items.length ?? 0}</p>
                <p className="text-sm text-ink-muted">
                  {primaryResume?.parseStatus === 'DONE' ? 'vị trí đạt ngưỡng' : 'Cần có CV đã phân tích'}
                </p>
              </div>
            )}
          </SummaryCard>
        </div>

        <RecommendedJobs alwaysShowViewAll />

        <section className="flex flex-col items-start justify-between gap-4 rounded-(--radius-card) bg-surface p-6 sm:flex-row sm:items-center">
          <div>
            <h2 className="text-lg font-semibold text-ink">Cải thiện CV của bạn</h2>
            <p className="mt-1 text-sm text-ink-muted">
              Nhận gợi ý từ khoá còn thiếu, đề xuất cải thiện từng mục, và lộ trình học tập phù hợp với CV hiện tại
              của bạn.
            </p>
          </div>
          {primaryResume ? (
            <Button asChild className="shrink-0">
              <Link to={`/candidate/resumes/${primaryResume.id}/improvement-suggestions`}>Xem gợi ý cải thiện</Link>
            </Button>
          ) : (
            <Button asChild className="shrink-0">
              <Link to="/candidate/profile">Tải CV lên để bắt đầu</Link>
            </Button>
          )}
        </section>
      </div>
    </CandidateLayout>
  )
}

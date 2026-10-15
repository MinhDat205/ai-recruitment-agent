import { ArrowLeft } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { CvQaPanel } from '../features/cvQa/CvQaPanel'
import { CvQaUnavailable } from '../features/cvQa/CvQaUnavailable'
import { CV_QA_TEXT } from '../features/cvQa/cvQaLabels'
import { useCvQaSession } from '../features/cvQa/useCvQaSession'
import { useResumesQuery } from '../features/resumes/queries'

const PROFILE_PATH = '/candidate/profile'

function ResumeListSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-hidden="true">
      <div className="h-4 w-full animate-pulse rounded bg-m3-surface-container" />
      <div className="h-4 w-5/6 animate-pulse rounded bg-m3-surface-container" />
      <div className="h-4 w-2/3 animate-pulse rounded bg-m3-surface-container" />
    </div>
  )
}

// FR-C08 - trang hoi dap CV phia ung vien (UI.md muc 4c, 4d, 6), route /candidate/resumes/:id/qa trong nhom
// ProtectedRoute(CANDIDATE). Lay CV tu danh sach CV cua chinh minh (khuon CvImprovementSuggestionsPage) - CV khong co
// trong danh sach (cua nguoi khac, khong ton tai) -> "Khong tim thay CV nay.", khong goi API hoi dap.
export function CandidateCvQaPage() {
  const { id } = useParams<{ id: string }>()
  const resumeId = id ?? ''
  // Goi o CAP TRANG, truoc moi return som (luat hook React, L6); khoa = :id -> doi CV tren cung trang xoa phien (R-N3).
  const session = useCvQaSession('candidate', resumeId)
  const { data: resumes, isLoading } = useResumesQuery()
  const resume = resumes?.find((r) => r.id === resumeId)

  function renderBody() {
    if (isLoading) {
      return <ResumeListSkeleton />
    }
    // Danh sach loi ma chua co du lieu cung roi vao nhanh nay - UI.md muc 6 khong co cau rieng (L52).
    if (!resume) {
      return (
        <div className="flex flex-col items-start gap-2">
          <p className="text-sm text-m3-on-surface">{CV_QA_TEXT.resumeNotFound}</p>
          <Link to={PROFILE_PATH} className="text-sm text-m3-primary hover:underline">
            {CV_QA_TEXT.backToProfile}
          </Link>
        </div>
      )
    }
    if (resume.parseStatus !== 'DONE') {
      return <CvQaUnavailable side="candidate" parseStatus={resume.parseStatus} />
    }
    return <CvQaPanel side="candidate" session={session} resumeId={resume.id} />
  }

  // Lien ket ve dau trang chi khi than trang chua co lien ket rieng (dang tai, DONE) - tranh hai lien ket trung (L53).
  const showTopBackLink = isLoading || resume?.parseStatus === 'DONE'

  return (
    <CandidateLayout>
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-8 md:px-6">
        {showTopBackLink && (
          <Link
            to={PROFILE_PATH}
            className="inline-flex w-fit items-center gap-1.5 text-sm text-m3-primary hover:underline"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            {CV_QA_TEXT.backToProfile}
          </Link>
        )}
        <div className="flex flex-col gap-1">
          <h1 className="text-m3-headline-sm text-m3-on-surface">{CV_QA_TEXT.pageTitle}</h1>
          {resume && (
            <p className="text-sm break-words text-m3-on-surface-variant">
              {resume.versionLabel ? `${resume.fileName} · ${resume.versionLabel}` : resume.fileName}
            </p>
          )}
        </div>
        <Card>
          <CardContent>{renderBody()}</CardContent>
        </Card>
      </div>
    </CandidateLayout>
  )
}

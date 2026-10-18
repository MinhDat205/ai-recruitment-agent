import { useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { createBuilderResume } from '../features/cvBuilder/api'
import { CV_BUILDER_TEXT } from '../features/cvBuilder/cvBuilderLabels'
import { EMPTY_FORM_VALUES } from '../features/cvBuilder/cvBuilderSchema'
import { CvBuilderFormView } from '../features/cvBuilder/CvBuilderFormView'
import type { CvBuilderRequest, CvBuilderSavedState } from '../features/cvBuilder/types'
import { RESUMES_QUERY_KEY } from '../features/resumes/queries'

const PROFILE_PATH = '/candidate/profile'

// FR-U12 UI.md muc 4a - trang tao CV, route /candidate/resumes/new trong nhom ProtectedRoute(CANDIDATE) +
// RequireCandidateProfileOnboarding. Luu (B1) thanh cong -> danh sach CV tai lai + ve trang ho so kem cau "Đã tạo CV."
// (UI.md muc 6 "Lưu xong").
export function CvBuilderPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  async function handleSave(request: CvBuilderRequest) {
    await createBuilderResume(request)
    void queryClient.invalidateQueries({ queryKey: RESUMES_QUERY_KEY })
    const state: CvBuilderSavedState = { cvBuilderSaved: 'create' }
    navigate(PROFILE_PATH, { state })
  }

  return (
    <CandidateLayout>
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-8 md:px-6">
        <Link
          to={PROFILE_PATH}
          className="inline-flex w-fit items-center gap-1.5 text-sm text-m3-on-primary-container hover:underline"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          {CV_BUILDER_TEXT.backToProfile}
        </Link>
        <div className="flex flex-col gap-1">
          <h1 className="text-m3-headline-sm text-m3-on-surface">{CV_BUILDER_TEXT.pageTitleCreate}</h1>
          <p className="text-sm text-m3-on-surface">{CV_BUILDER_TEXT.intro}</p>
        </div>
        <CvBuilderFormView mode="create" initial={EMPTY_FORM_VALUES} onSave={handleSave} />
      </div>
    </CandidateLayout>
  )
}

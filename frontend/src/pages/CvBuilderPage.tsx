import { useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { ArrowLeft } from 'lucide-react'
import { useMemo } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { createBuilderResume, getBuilderDocument } from '../features/cvBuilder/api'
import { CV_BUILDER_TEXT } from '../features/cvBuilder/cvBuilderLabels'
import { EMPTY_FORM_VALUES, formValuesFromDocument } from '../features/cvBuilder/cvBuilderSchema'
import { CvBuilderFormView, type CvBuilderMode } from '../features/cvBuilder/CvBuilderFormView'
import type { CvBuilderDocumentResponse, CvBuilderRequest, CvBuilderSavedState } from '../features/cvBuilder/types'
import { RESUMES_QUERY_KEY, useResumesQuery } from '../features/resumes/queries'
import { extractErrorMessage } from '../lib/httpError'

const PROFILE_PATH = '/candidate/profile'
// R-B2 - CV co nhung khong dung bang cong cu (CV tai len).
const NOT_FROM_BUILDER_ERROR = 'CV_NOT_FROM_BUILDER'

// B2 - form da luu khong bao gio doi (sua = CV moi, R-W6) nen khong can tai lai khi quay lai tab.
function builderDocumentKey(resumeId: string) {
  return ['cv-builder-document', resumeId]
}

// 4xx la ket qua chot, thu lai khong doi gi - chi thu lai 1 lan voi loi mang/5xx (khuon candidateApplicationDetail).
function retryUnlessClientError(failureCount: number, error: unknown): boolean {
  if (isAxiosError(error)) {
    const status = error.response?.status
    if (status !== undefined && status >= 400 && status < 500) {
      return false
    }
  }
  return failureCount < 1
}

// UI.md muc 4d, 6 - 400 (id khong phai UUID), 403, 404 cung mot cau "Không tìm thấy CV này."; 409 CV_NOT_FROM_BUILDER ->
// cau backend; loi khac (mang, 5xx) -> cau du phong.
function loadErrorMessage(error: unknown): string {
  if (isAxiosError(error)) {
    const status = error.response?.status
    if (status === 400 || status === 403 || status === 404) {
      return CV_BUILDER_TEXT.resumeNotFound
    }
    const code = (error.response?.data as { error?: unknown } | undefined)?.error
    if (status === 409 && code === NOT_FROM_BUILDER_ERROR) {
      return extractErrorMessage(error, CV_BUILDER_TEXT.editLoadFailed)
    }
  }
  return CV_BUILDER_TEXT.editLoadFailed
}

function BackLink() {
  return (
    <Link
      to={PROFILE_PATH}
      className="inline-flex w-fit items-center gap-1.5 text-sm text-m3-on-primary-container hover:underline"
    >
      <ArrowLeft className="h-4 w-4" aria-hidden="true" />
      {CV_BUILDER_TEXT.backToProfile}
    </Link>
  )
}

// UI.md muc 6 "Đang tải (sửa)" - 4 the gia, khong form. Thanh pulse bg-m3-surface-container nam tren the trang
// (nen trang cung mau surface-container nen thanh dat thang tren nen se khong thay).
function EditSkeleton() {
  return (
    <div className="flex flex-col gap-4" aria-hidden="true">
      {[0, 1, 2, 3].map((index) => (
        <div
          key={index}
          className="flex flex-col gap-3 rounded-m3-md border border-m3-outline-variant bg-m3-surface p-6"
        >
          <div className="h-5 w-1/3 animate-pulse rounded-m3-xs bg-m3-surface-container" />
          <div className="h-10 w-full animate-pulse rounded-m3-xs bg-m3-surface-container" />
          <div className="h-10 w-2/3 animate-pulse rounded-m3-xs bg-m3-surface-container" />
        </div>
      ))}
    </div>
  )
}

// UI.md muc 4c - form khoi tao bang B2; tieu de phu "{fileName} · {versionLabel}" + editNotice. KHONG co the "Điền sẵn"
// (CvBuilderFormView chi hien the nay o mode create).
function EditContent({ resumeId, onSave }: { resumeId: string; onSave: (request: CvBuilderRequest) => Promise<void> }) {
  const documentQuery = useQuery({
    queryKey: builderDocumentKey(resumeId),
    queryFn: () => getBuilderDocument(resumeId),
    retry: retryUnlessClientError,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })
  const { data: resumes } = useResumesQuery()
  const fileName = resumes?.find((resume) => resume.id === resumeId)?.fileName

  // Co du lieu thi giu form du lan tai lai sau co loi (TanStack giu data khi refetch loi).
  if (documentQuery.data) {
    return <EditForm document={documentQuery.data} fileName={fileName} onSave={onSave} />
  }
  if (documentQuery.isPending) {
    return (
      <>
        <PageHeading mode="edit" />
        <EditSkeleton />
      </>
    )
  }
  return (
    <>
      <PageHeading mode="edit" />
      <p role="alert" className="text-sm text-m3-on-surface">
        {loadErrorMessage(documentQuery.error)}
      </p>
    </>
  )
}

function EditForm({
  document,
  fileName,
  onSave,
}: {
  document: CvBuilderDocumentResponse
  fileName: string | undefined
  onSave: (request: CvBuilderRequest) => Promise<void>
}) {
  const initial = useMemo(() => formValuesFromDocument(document), [document])
  const subtitle =
    fileName === undefined ? null : document.versionLabel ? `${fileName} · ${document.versionLabel}` : fileName

  return (
    <>
      <div className="flex flex-col gap-1">
        <h1 className="text-m3-headline-sm text-m3-on-surface">{CV_BUILDER_TEXT.pageTitleEdit}</h1>
        {subtitle && <p className="text-sm break-words text-m3-on-surface">{subtitle}</p>}
        <p className="text-sm text-m3-on-surface">{CV_BUILDER_TEXT.editNotice}</p>
      </div>
      <CvBuilderFormView mode="edit" initial={initial} onSave={onSave} />
    </>
  )
}

function PageHeading({ mode }: { mode: CvBuilderMode }) {
  return (
    <h1 className="text-m3-headline-sm text-m3-on-surface">
      {mode === 'create' ? CV_BUILDER_TEXT.pageTitleCreate : CV_BUILDER_TEXT.pageTitleEdit}
    </h1>
  )
}

// FR-U12 UI.md muc 4a/4c/4d - mot trang cho ca tao (/candidate/resumes/new) va sua (/candidate/resumes/:id/edit), trong
// nhom ProtectedRoute(CANDIDATE) + RequireCandidateProfileOnboarding. Ca hai che do luu bang B1 - sua cung tao CV MOI,
// CV cu khong doi (R-W6). Luu xong -> danh sach CV tai lai + ve trang ho so kem cau savedCreate/savedEdit (UI.md muc 6).
export function CvBuilderPage() {
  const { id } = useParams<{ id: string }>()
  const mode: CvBuilderMode = id === undefined ? 'create' : 'edit'
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  async function handleSave(request: CvBuilderRequest) {
    await createBuilderResume(request)
    void queryClient.invalidateQueries({ queryKey: RESUMES_QUERY_KEY })
    const state: CvBuilderSavedState = { cvBuilderSaved: mode }
    navigate(PROFILE_PATH, { state })
  }

  return (
    <CandidateLayout>
      <div className="mx-auto flex w-full max-w-3xl flex-col gap-4 px-4 py-8 md:px-6">
        <BackLink />
        {id === undefined ? (
          <>
            <div className="flex flex-col gap-1">
              <PageHeading mode="create" />
              <p className="text-sm text-m3-on-surface">{CV_BUILDER_TEXT.intro}</p>
            </div>
            <CvBuilderFormView mode="create" initial={EMPTY_FORM_VALUES} onSave={handleSave} />
          </>
        ) : (
          // key: doi CV dang sua -> dung lai form tu dau (defaultValues chi doc mot lan).
          <EditContent key={id} resumeId={id} onSave={handleSave} />
        )}
      </div>
    </CandidateLayout>
  )
}

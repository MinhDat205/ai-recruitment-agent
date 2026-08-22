import { isAxiosError } from 'axios'
import { Loader2, Sparkles } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { PublicLayout } from '../components/layout/PublicLayout'
import { useCvImprovementQuery, useRequestCvImprovementMutation, useResumesQuery } from '../features/resumes/queries'
import type { CvImprovementSectionSuggestion } from '../features/resumes/types'

// Ap dung khi khong co tin tuyen dung cung linh vuc de doi chieu (backend: cv-improvement-v1.st,
// muc "Field-matching rule" - missingKeywords/learningPath duoc LLM co y de rong trong truong hop
// nay). Noi ro NGUYEN NHAN (thieu du lieu thi truong), KHONG dung tu "loi"/"that bai" - day la
// trang thai hop le, khong phai su co he thong.
const EMPTY_MISSING_KEYWORDS_TEXT =
  'Hiện chưa có tin tuyển dụng cùng lĩnh vực để đối chiếu, nên chưa có từ khoá kỹ năng nào được gợi ý thêm.'
const EMPTY_LEARNING_PATH_TEXT =
  'Hiện chưa có tin tuyển dụng cùng lĩnh vực để đối chiếu, nên chưa có lộ trình học tập nào được gợi ý.'
// Khac hai cau tren - muc nay luon dua tren chinh noi dung CV (khong can tin thi truong), nen rong
// o day la truong hop hiem, khong gan voi ly do "thieu tin cung linh vuc".
const EMPTY_SECTION_SUGGESTIONS_TEXT = 'Chưa có gợi ý chỉnh sửa nào cho mục này.'

// Backend tra loi qua ErrorResponse { error, message } (xem GlobalExceptionHandler) - mau
// extractErrorMessage cua ResumeList.tsx/CandidateProfilePage.tsx.
interface SectionSuggestionGroup {
  section: string
  suggestions: CvImprovementSectionSuggestion[]
}

// Gom cac phan tu cung section thanh MOT nhom - tieu de section chi in mot lan, tranh lap lai lien
// tiep (loi phat hien qua test tay: "Kinh nghiem lam viec" lap 3 lan). Giu THU TU XUAT HIEN DAU TIEN
// cua moi section (Map.set() lan dau quyet dinh vi tri trong groups[], khong sap xep lai) - dung
// tinh than voi backend: LLM tra ve sectionSuggestions theo thu tu no chon, khong co quy uoc thu tu
// co dinh nao khac de dua vao.
function groupBySection(items: CvImprovementSectionSuggestion[]): SectionSuggestionGroup[] {
  const groups: SectionSuggestionGroup[] = []
  const groupIndexBySection = new Map<string, number>()
  for (const item of items) {
    const existingIndex = groupIndexBySection.get(item.section)
    if (existingIndex === undefined) {
      groupIndexBySection.set(item.section, groups.length)
      groups.push({ section: item.section, suggestions: [item] })
    } else {
      groups[existingIndex].suggestions.push(item)
    }
  }
  return groups
}

function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

function extractErrorCode(err: unknown): string | undefined {
  if (isAxiosError(err)) {
    const data = err.response?.data as { error?: unknown } | undefined
    if (data && typeof data.error === 'string' && data.error.length > 0) {
      return data.error
    }
  }
  return undefined
}

// Backend tra ve DUNG mot trong ba tinh huong khi GET improvement-suggestions loi:
// RESUME_NOT_FOUND (khong so huu/CV khong ton tai - thu lai vo ich, KHONG moi thu lai),
// RESUME_PARSED_DATA_NOT_FOUND (CV chua parse xong - trang thai hop le, khong phai loi he thong,
// huong dan quay lai trang ho so), con lai (mang, 500...) - giu cau chung moi thu lai. CHI dung
// error CODE de CHON cau, khong hien thi error/message tho tu backend (co the chua UUID ky thuat).
function renderErrorMessage(errorCode: string | undefined): ReactNode {
  if (errorCode === 'RESUME_NOT_FOUND') {
    return 'Không tìm thấy CV này.'
  }
  if (errorCode === 'RESUME_PARSED_DATA_NOT_FOUND') {
    return (
      <>
        CV này chưa được xử lý xong nên chưa tạo được gợi ý. Vui lòng{' '}
        <Link to="/candidate/profile" className="text-brand hover:underline">
          quay lại trang hồ sơ
        </Link>{' '}
        để xem trạng thái xử lý CV.
      </>
    )
  }
  return 'Không tải được trạng thái gợi ý, vui lòng thử lại.'
}

export function CvImprovementSuggestionsPage() {
  const { id } = useParams<{ id: string }>()
  const resumeId = id ?? ''
  const { data, isLoading, isError, error } = useCvImprovementQuery(resumeId)
  const requestMutation = useRequestCvImprovementMutation(resumeId)
  const { data: resumes } = useResumesQuery()
  const resume = resumes?.find((r) => r.id === resumeId)

  return (
    <PublicLayout>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-4 py-8 md:px-6">
        <Card>
          <CardHeader>
            <CardTitle>Gợi ý cải thiện CV</CardTitle>
            {resume && <p className="text-sm text-ink-muted">{resume.fileName}</p>}
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {isLoading && <p className="text-sm text-ink-muted">Đang tải...</p>}
            {isError && <p className="text-sm text-danger">{renderErrorMessage(extractErrorCode(error))}</p>}

            {!isLoading && !isError && data?.status === 'NOT_REQUESTED' && (
              <div className="flex flex-col items-start gap-3">
                <p className="text-sm text-ink-muted">
                  Nhận gợi ý cụ thể để cải thiện CV này, dựa trên các tin tuyển dụng đang mở cùng lĩnh vực.
                </p>
                <Button type="button" onClick={() => requestMutation.mutate()} disabled={requestMutation.isPending}>
                  <Sparkles className="h-4 w-4" aria-hidden="true" />
                  Xin gợi ý cải thiện CV
                </Button>
                {requestMutation.isError && (
                  <p className="text-sm text-danger">
                    {extractErrorMessage(requestMutation.error, 'Không gửi được yêu cầu, vui lòng thử lại.')}
                  </p>
                )}
              </div>
            )}

            {!isLoading && !isError && (data?.status === 'PENDING' || data?.status === 'RUNNING') && (
              <div className="flex items-center gap-2 text-sm text-ink-muted">
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                Đang tạo gợi ý...
              </div>
            )}

            {!isLoading && !isError && data?.status === 'FAILED' && (
              <div className="flex flex-col items-start gap-3">
                <p className="text-sm text-danger">Tạo gợi ý thất bại, vui lòng thử lại.</p>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => requestMutation.mutate()}
                  disabled={requestMutation.isPending}
                >
                  Thử lại
                </Button>
                {requestMutation.isError && (
                  <p className="text-sm text-danger">
                    {extractErrorMessage(requestMutation.error, 'Không gửi được yêu cầu, vui lòng thử lại.')}
                  </p>
                )}
              </div>
            )}
          </CardContent>
        </Card>

        {!isLoading && !isError && data?.status === 'DONE' && (
          <>
            <Card>
              <CardHeader>
                <CardTitle>Từ khoá kỹ năng còn thiếu</CardTitle>
              </CardHeader>
              <CardContent>
                {data.missingKeywords.length === 0 ? (
                  <p className="text-sm text-ink-muted">{EMPTY_MISSING_KEYWORDS_TEXT}</p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {data.missingKeywords.map((keyword) => (
                      <span
                        key={keyword}
                        className="rounded-(--radius-badge) bg-brand-light px-2 py-1 text-sm text-brand"
                      >
                        {keyword}
                      </span>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Gợi ý chỉnh sửa từng mục</CardTitle>
              </CardHeader>
              <CardContent>
                {data.sectionSuggestions.length === 0 ? (
                  <p className="text-sm text-ink-muted">{EMPTY_SECTION_SUGGESTIONS_TEXT}</p>
                ) : (
                  <div className="flex flex-col gap-4">
                    {groupBySection(data.sectionSuggestions).map((group) => (
                      <div
                        key={group.section}
                        className="flex flex-col gap-2 border-b border-line pb-4 last:border-b-0 last:pb-0"
                      >
                        <span className="text-sm font-medium text-ink">{group.section}</span>
                        <ul className="flex list-disc flex-col gap-1 pl-5">
                          {group.suggestions.map((item) => (
                            <li key={item.section + item.suggestion} className="text-sm text-ink-muted">
                              {item.suggestion}
                            </li>
                          ))}
                        </ul>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Lộ trình học tập/chứng chỉ</CardTitle>
              </CardHeader>
              <CardContent>
                {data.learningPath.length === 0 ? (
                  <p className="text-sm text-ink-muted">{EMPTY_LEARNING_PATH_TEXT}</p>
                ) : (
                  <ul className="flex flex-col gap-3">
                    {data.learningPath.map((item) => (
                      <li
                        key={item.topic}
                        className="flex flex-col gap-1 border-b border-line pb-3 last:border-b-0 last:pb-0"
                      >
                        <span className="text-sm font-medium text-ink">{item.topic}</span>
                        <span className="text-sm text-ink-muted">{item.reason}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          </>
        )}
      </div>
    </PublicLayout>
  )
}

import { isAxiosError } from 'axios'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { useResumesQuery } from '../resumes/queries'
import { useCreateApplicationMutation } from './queries'

// Backend tra loi qua ErrorResponse { error, message } (xem GlobalExceptionHandler) - lay dung
// message do thay vi chuoi cung, chi fallback khi response khong dung dang nay (vd loi mang).
// Bao phu ca 409 APPLICATION_DUPLICATE lan 400 tu Bean Validation.
function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

export function JobApplyForm({ jobId }: { jobId: string }) {
  const { data: resumes, isLoading: resumesLoading, isError: resumesError } = useResumesQuery()
  const [selectedResumeId, setSelectedResumeId] = useState<string | undefined>(undefined)
  const [coverLetter, setCoverLetter] = useState('')
  const [consentChecked, setConsentChecked] = useState(false)
  const createMutation = useCreateApplicationMutation()

  // Mac dinh chon CV chinh (is_primary) neu co, ung vien van doi duoc sang CV khac. Tinh truc
  // tiep khi render thay vi dong bo qua useEffect+setState (tranh cascading render).
  const defaultResumeId =
    resumes && resumes.length > 0 ? (resumes.find((r) => r.isPrimary) ?? resumes[0]).id : undefined
  const resumeId = selectedResumeId ?? defaultResumeId

  const onSubmit = () => {
    if (!resumeId || !consentChecked) {
      return
    }
    // mutate (khong await mutateAsync): loi da duoc phan anh qua createMutation.isError +
    // extractErrorMessage ben duoi, khong can bat thu cong - tranh unhandled promise rejection
    // moi lan nop trung (409).
    createMutation.mutate({
      jobId,
      resumeId,
      aiConsent: consentChecked,
      coverLetter: coverLetter.trim() || undefined,
    })
  }

  if (createMutation.isSuccess) {
    return (
      <div className="rounded-(--radius-card) border border-line bg-brand-light p-6">
        <p className="text-sm font-medium text-brand">Nộp đơn thành công.</p>
        <p className="mt-1 text-sm text-ink-muted">
          Hồ sơ của bạn đã được gửi tới nhà tuyển dụng. Bạn có thể theo dõi trạng thái ứng tuyển sau.
        </p>
        <Link to="/" className="mt-3 inline-block text-sm text-brand hover:underline">
          Về trang danh sách việc làm
        </Link>
      </div>
    )
  }

  if (!resumesLoading && !resumesError && (!resumes || resumes.length === 0)) {
    return (
      <div className="rounded-(--radius-card) border border-line bg-surface p-6">
        <p className="text-sm text-ink-muted">Bạn chưa có CV nào. Hãy tải lên CV trước khi ứng tuyển.</p>
        <Link to="/candidate/profile" className="mt-3 inline-block text-sm text-brand hover:underline">
          Tải CV lên
        </Link>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6 rounded-(--radius-card) border border-line bg-surface p-6">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="apply-resume">Chọn CV</Label>
        {resumesLoading && <p className="text-sm text-ink-muted">Đang tải danh sách CV...</p>}
        {resumesError && <p className="text-sm text-danger">Không tải được danh sách CV.</p>}
        {resumes && resumes.length > 0 && (
          <Select value={resumeId} onValueChange={setSelectedResumeId}>
            <SelectTrigger id="apply-resume" className="w-full">
              <SelectValue placeholder="Chọn CV" />
            </SelectTrigger>
            <SelectContent>
              {resumes.map((resume) => (
                <SelectItem key={resume.id} value={resume.id}>
                  {resume.fileName}
                  {resume.isPrimary ? ' (CV chính)' : ''}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="apply-cover-letter">Thư giới thiệu (tuỳ chọn)</Label>
        <Textarea
          id="apply-cover-letter"
          placeholder="Giới thiệu ngắn gọn về bản thân và lý do bạn phù hợp với vị trí này"
          value={coverLetter}
          onChange={(e) => setCoverLetter(e.target.value)}
        />
      </div>

      <div className="group/field flex items-start gap-2.5">
        <Checkbox
          id="apply-consent"
          checked={consentChecked}
          onCheckedChange={(checked) => setConsentChecked(checked === true)}
        />
        <Label htmlFor="apply-consent" className="text-sm font-normal text-ink">
          Tôi đồng ý cho phép CV của tôi được hệ thống AI phân tích và chấm điểm để hỗ trợ nhà tuyển
          dụng đánh giá hồ sơ.
        </Label>
      </div>

      {createMutation.isError && (
        <p className="text-sm text-danger">
          {extractErrorMessage(createMutation.error, 'Nộp đơn thất bại, vui lòng thử lại.')}
        </p>
      )}

      <Button
        type="button"
        onClick={onSubmit}
        disabled={!resumeId || !consentChecked || createMutation.isPending}
        className="w-fit"
      >
        {createMutation.isPending ? 'Đang nộp đơn...' : 'Nộp đơn'}
      </Button>
    </div>
  )
}

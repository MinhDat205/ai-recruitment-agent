import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { useMemo, useRef } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import {
  buildApplyFormSchema,
  mergeAnswersAfterReload,
  toAnswerInputs,
  type ApplyFormValues,
} from '../candidateScreening/applyFormSchema'
import { CANDIDATE_SCREENING_TEXT } from '../candidateScreening/candidateScreeningLabels'
import { useScreeningFormQuery } from '../candidateScreening/queries'
import {
  ScreeningQuestionsFieldset,
  type ScreeningFormStatus,
} from '../candidateScreening/ScreeningQuestionsFieldset'
import type { ScreeningQuestionResponse } from '../screening/types'
import { useResumesQuery } from '../resumes/queries'
import { useCreateApplicationMutation } from './queries'

const QUESTIONS_CHANGED = 'SCREENING_QUESTIONS_CHANGED'
const NO_QUESTIONS: readonly ScreeningQuestionResponse[] = []

const DEFAULT_VALUES: ApplyFormValues = {
  resumeId: '',
  coverLetter: '',
  aiConsent: false,
  talentPoolConsent: false,
  answers: {},
}

// Backend tra loi qua ErrorResponse { error, message } (xem GlobalExceptionHandler) - lay dung message do thay vi chuoi
// cung. 400 Bean Validation (aiConsent, jobId...) tra map truong -> cau: lay cau dau tien cua map (FR-U09 R-C6), truoc
// day roi ve cau fallback. Chi fallback khi response khong co chu nao (vd loi mang).
function extractApplyErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data
    if (data && typeof data === 'object') {
      const record = data as Record<string, unknown>
      if (typeof record.message === 'string' && record.message.length > 0) {
        return record.message
      }
      const first = Object.values(record).find((value): value is string => typeof value === 'string' && value !== '')
      if (first) {
        return first
      }
    }
  }
  return fallback
}

function errorCodeOf(err: unknown): string | undefined {
  if (isAxiosError(err)) {
    const data = err.response?.data as { error?: unknown } | undefined
    return typeof data?.error === 'string' ? data.error : undefined
  }
  return undefined
}

export function JobApplyForm({ jobId }: { jobId: string }) {
  const { data: allResumes, isLoading: resumesLoading, isError: resumesError } = useResumesQuery()
  const screeningQuery = useScreeningFormQuery(jobId)
  const createMutation = useCreateApplicationMutation()
  const screeningHeadingRef = useRef<HTMLHeadingElement>(null)

  const questions = screeningQuery.data?.questions ?? NO_QUESTIONS
  // Schema dung lai theo bo cau hoi hien tai; RHF doc resolver moi o moi lan render (control._options = props).
  const resolver = useMemo(() => zodResolver(buildApplyFormSchema(questions)), [questions])
  const {
    register,
    control,
    handleSubmit,
    getValues,
    setValue,
    formState: { errors },
  } = useForm<ApplyFormValues>({ resolver, defaultValues: DEFAULT_VALUES })
  const selectedResumeId = useWatch({ control, name: 'resumeId' })
  const consentChecked = useWatch({ control, name: 'aiConsent' })

  // CV parseStatus=FAILED khong the cham diem duoc (AI chua trich xuat xong noi dung) - loai khoi
  // danh sach chon truoc khi tinh default/render, tranh ung vien nop don bang mot CV hong khien don
  // do "chet", HR khong bao gio bam cham diem duoc (xem docs/ROADMAP.md, phat hien khi kiem thu
  // Phase D bang key that).
  const resumes = allResumes?.filter((r) => r.parseStatus !== 'FAILED')
  const hiddenFailedCount = allResumes ? allResumes.length - (resumes?.length ?? 0) : 0

  // Mac dinh chon CV chinh (is_primary) neu co, ung vien van doi duoc sang CV khac. Tinh truc
  // tiep khi render thay vi dong bo qua useEffect+setState (tranh cascading render). Chi xet trong
  // danh sach DA LOC - khong bao gio mac dinh chon nham mot CV FAILED.
  const defaultResumeId =
    resumes && resumes.length > 0 ? (resumes.find((r) => r.isPrimary) ?? resumes[0]).id : undefined
  const resumeId = selectedResumeId || defaultResumeId

  // Trang thai F1 (UI.md muc 6): 404 = tin vua dong/het han sau khi trang da tai tin.
  const screeningStatus: ScreeningFormStatus = screeningQuery.data
    ? 'ready'
    : screeningQuery.isLoading
      ? 'loading'
      : isAxiosError(screeningQuery.error) && screeningQuery.error.response?.status === 404
        ? 'notAccepting'
        : 'error'
  // Khoa nop khi chua co revision (F1 dang tai/loi) va trong luc tai lai F1 sau 409 - tranh gui revision cu.
  const screeningBlocked = screeningStatus !== 'ready' || screeningQuery.isFetching
  const screeningUnavailable = screeningStatus === 'error'

  const conflict = createMutation.isError && errorCodeOf(createMutation.error) === QUESTIONS_CHANGED
  // Tin co 0 cau thi KHONG co khoi cau hoi (R-F1); tai lan dau/loi/404 van hien khoi de bao trang thai.
  const showScreeningBlock = screeningStatus !== 'ready' || questions.length > 0
  const conflictMessage = conflict
    ? extractApplyErrorMessage(createMutation.error, CANDIDATE_SCREENING_TEXT.questionsChanged)
    : null

  // R-F5 - 409: tai lai F1, giu cau tra loi cua cau con cung id + cung loai (SINGLE_CHOICE: phuong an con ton tai), xoa
  // phan con lai; roi dua focus ve tieu de khoi cau hoi (UI.md muc 9). O CV, thu gioi thieu, hai o dong y giu nguyen.
  async function reloadQuestionsAfterConflict(previous: readonly ScreeningQuestionResponse[]) {
    const result = await screeningQuery.refetch()
    if (result.data) {
      setValue('answers', mergeAnswersAfterReload(previous, result.data.questions, getValues('answers')))
    }
    screeningHeadingRef.current?.focus()
  }

  // Goi qua handleSubmit TRONG su kien submit cua form (khong dung handleSubmit luc render): ham nay cham ref tieu de
  // khoi cau hoi qua reloadQuestionsAfterConflict (react-hooks/refs).
  function submitValues(values: ApplyFormValues) {
    const screening = screeningQuery.data
    if (!resumeId || !values.aiConsent || !screening) {
      return
    }
    // mutate (khong await mutateAsync): loi da duoc phan anh qua createMutation.isError +
    // extractApplyErrorMessage ben duoi, khong can bat thu cong - tranh unhandled promise rejection
    // moi lan nop trung (409).
    createMutation.mutate(
      {
        jobId,
        resumeId,
        aiConsent: values.aiConsent,
        coverLetter: values.coverLetter.trim() || undefined,
        // R-F4: luon gui revision cua F1, ke ca tin 0 cau.
        screeningRevision: screening.revision,
        answers: toAnswerInputs(screening.questions, values.answers),
        talentPoolConsent: values.talentPoolConsent,
      },
      {
        onError: (error) => {
          if (errorCodeOf(error) === QUESTIONS_CHANGED) {
            void reloadQuestionsAfterConflict(screening.questions)
          }
        },
      },
    )
  }

  if (createMutation.isSuccess) {
    return (
      <div className="rounded-(--radius-card) border border-m3-outline-variant bg-m3-primary-container p-6">
        <p className="text-sm font-medium text-m3-on-primary-container">Nộp đơn thành công.</p>
        <p className="mt-1 text-sm text-m3-on-surface">
          Hồ sơ của bạn đã được gửi tới nhà tuyển dụng. Bạn có thể theo dõi trạng thái ứng tuyển sau.
        </p>
        <div className="mt-3 flex flex-wrap gap-x-6 gap-y-2">
          <Link
            to={`/candidate/applications/${createMutation.data.id}`}
            className="inline-block text-sm text-m3-on-primary-container hover:underline"
          >
            {CANDIDATE_SCREENING_TEXT.viewApplication}
          </Link>
          <Link to="/" className="inline-block text-sm text-m3-on-primary-container hover:underline">
            Về trang danh sách việc làm
          </Link>
        </div>
      </div>
    )
  }

  if (!resumesLoading && !resumesError && (!resumes || resumes.length === 0)) {
    return (
      <div className="rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-6">
        <p className="text-sm text-m3-on-surface-variant">Bạn chưa có CV nào. Hãy tải lên CV trước khi ứng tuyển.</p>
        <Link to="/candidate/profile" className="mt-3 inline-block text-sm text-m3-primary hover:underline">
          Tải CV lên
        </Link>
      </div>
    )
  }

  // 409 khi tin KHONG co khoi cau hoi (vd revision lech nhung tin van 0 cau): khong co cho dat thong bao dau khoi, hien
  // o dong loi chung (L20). Moi loi khac (400 INVALID_SCREENING_ANSWER, 409 APPLICATION_DUPLICATE, 404...) o dong loi chung.
  const showGeneralError = createMutation.isError && !(conflict && showScreeningBlock)
  const submitDisabled = !resumeId || !consentChecked || createMutation.isPending || screeningBlocked

  return (
    <form
      onSubmit={(event) => {
        void handleSubmit(submitValues)(event)
      }}
      noValidate
      className="flex flex-col gap-6 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-6"
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="apply-resume">Chọn CV</Label>
        {resumesLoading && <p className="text-sm text-m3-on-surface-variant">Đang tải danh sách CV...</p>}
        {resumesError && <p className="text-sm text-m3-error">Không tải được danh sách CV.</p>}
        {resumes && resumes.length > 0 && (
          <Controller
            control={control}
            name="resumeId"
            render={({ field }) => (
              <Select value={resumeId} onValueChange={field.onChange}>
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
          />
        )}
        {hiddenFailedCount > 0 && (
          <p className="text-xs text-m3-on-surface-variant">
            {hiddenFailedCount} CV không thể chọn do phân tích thất bại, xem trong hồ sơ CV của bạn.
          </p>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="apply-cover-letter">Thư giới thiệu (tuỳ chọn)</Label>
        <Textarea
          id="apply-cover-letter"
          placeholder="Giới thiệu ngắn gọn về bản thân và lý do bạn phù hợp với vị trí này"
          {...register('coverLetter')}
        />
      </div>

      {showScreeningBlock && (
        <ScreeningQuestionsFieldset
          status={screeningStatus}
          questions={questions}
          conflictMessage={conflictMessage}
          headingRef={screeningHeadingRef}
          register={register}
          setValue={setValue}
          control={control}
          errors={errors}
          disabled={createMutation.isPending}
          onRetry={() => void screeningQuery.refetch()}
        />
      )}

      <Controller
        control={control}
        name="aiConsent"
        render={({ field }) => (
          <div className="group/field flex items-start gap-2.5">
            <Checkbox
              id="apply-consent"
              checked={field.value}
              onCheckedChange={(checked) => field.onChange(checked === true)}
            />
            <Label htmlFor="apply-consent" className="text-sm font-normal text-m3-on-surface">
              Tôi đồng ý cho phép CV của tôi được hệ thống AI phân tích và chấm điểm để hỗ trợ nhà tuyển
              dụng đánh giá hồ sơ.
            </Label>
          </div>
        )}
      />

      {/* FR-U09 R-K1 - o RIENG, khong tick san, KHONG chan nut Nop (UI_GUIDE muc 4). */}
      <Controller
        control={control}
        name="talentPoolConsent"
        render={({ field }) => (
          <div className="group/field flex items-start gap-2.5">
            <Checkbox
              id="apply-talent-pool"
              checked={field.value}
              onCheckedChange={(checked) => field.onChange(checked === true)}
              aria-describedby="apply-talent-pool-help"
            />
            <div className="flex min-w-0 flex-col gap-1">
              <Label htmlFor="apply-talent-pool" className="text-sm font-normal text-m3-on-surface">
                {CANDIDATE_SCREENING_TEXT.talentPoolLabel}
              </Label>
              <p id="apply-talent-pool-help" className="text-m3-body-sm text-m3-on-surface-variant">
                {CANDIDATE_SCREENING_TEXT.talentPoolHelp}
              </p>
            </div>
          </div>
        )}
      />

      {showGeneralError && (
        <p className="text-sm text-m3-error">
          {extractApplyErrorMessage(createMutation.error, 'Nộp đơn thất bại, vui lòng thử lại.')}
        </p>
      )}

      {screeningUnavailable && (
        <p id="apply-submit-blocked" className="text-sm text-m3-on-surface-variant">
          {CANDIDATE_SCREENING_TEXT.submitBlocked}
        </p>
      )}
      <Button
        type="submit"
        disabled={submitDisabled}
        aria-describedby={screeningUnavailable ? 'apply-submit-blocked' : undefined}
        className="w-fit"
      >
        {createMutation.isPending ? 'Đang nộp đơn...' : 'Nộp đơn'}
      </Button>
    </form>
  )
}

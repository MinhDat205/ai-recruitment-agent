import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { Controller, useForm, useWatch, type FieldErrors } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { useState } from 'react'
import { HrLayout } from '../components/layout/HrLayout'
import { extractErrorMessage } from '../lib/httpError'
import { useCatalogsQuery } from '../features/catalog/queries'
import { CATEGORY_HELPER_TEXT, LOCATION_HELPER_TEXT } from '../features/jobs/catalogDisplay'
import { CatalogField } from '../features/jobs/CatalogField'
import { EMPLOYMENT_TYPE_LABELS, EMPLOYMENT_TYPE_OPTIONS, WORK_MODE_LABELS, WORK_MODE_OPTIONS } from '../features/jobs/jobLabels'
import { useCreateHrJobMutation } from '../features/jobs/ownerQueries'
import type { JobCreateOwnerRequest } from '../features/jobs/ownerTypes'
import { AiDraftInputPanel, PartStepStatus } from '../features/jobDraft/AiDraftInputPanel'
import { AiSuggestedMark } from '../features/jobDraft/AiSuggestedMark'
import { CriteriaSuggestionStep } from '../features/jobDraft/CriteriaSuggestionStep'
import {
  AI_STEP_TITLES,
  FILLABLE_FIELD_LABELS,
  JOB_DRAFT_TEXT,
  type FillableField,
} from '../features/jobDraft/jobDraftLabels'
import {
  criterionNameError,
  focusSoon,
  totalWeight,
  weightError,
  weightInputId,
} from '../features/jobDraft/jobDraftValidation'
import { MissingFieldsCard } from '../features/jobDraft/MissingFieldsCard'
import { OverwriteFieldsDialog } from '../features/jobDraft/OverwriteFieldsDialog'
import { PostingSuggestionBlock } from '../features/jobDraft/PostingSuggestionBlock'
import { useSaveJobDraftMutation } from '../features/jobDraft/queries'
import { ScreeningSuggestionStep } from '../features/jobDraft/ScreeningSuggestionStep'
import type { JobAiDraftSaveRequest, JobDraft, JobDraftField } from '../features/jobDraft/types'
import { useJobAiDraft } from '../features/jobDraft/useJobAiDraft'

function todayIso(): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const TODAY_ISO = todayIso()

const createJobSchema = z
  .object({
    title: z.string().trim().min(1, 'Vui lòng nhập tiêu đề').max(200, 'Tối đa 200 ký tự'),
    description: z.string().trim().min(1, 'Vui lòng nhập mô tả công việc'),
    requirements: z.string().optional(),
    // FR-H11 R-P7 - chi o che do AI; luong thu cong khong gui truong nay.
    benefits: z.string().optional(),
    // FR-C05: ma danh muc, null = chua chon. Tuy chon o DRAFT; bat buoc khi mo tin do backend kiem (R-J3).
    categoryCode: z.string().nullable(),
    locationCode: z.string().nullable(),
    employmentType: z.string().optional(),
    workMode: z.string().optional(),
    salaryMin: z.string().optional(),
    salaryMax: z.string().optional(),
    salaryCurrency: z.string().optional(),
    deadline: z.string().optional(),
    subject: z.string().trim().min(1, 'Vui lòng nhập tiêu đề thư mời').max(255, 'Tối đa 255 ký tự'),
    body: z.string().trim().min(1, 'Vui lòng nhập nội dung thư mời'),
    senderName: z.string().trim().min(1, 'Vui lòng nhập tên người gửi').max(150, 'Tối đa 150 ký tự'),
    senderTitle: z.string().max(150, 'Tối đa 150 ký tự').optional(),
    address: z.string().optional(),
  })
  .refine((data) => !data.salaryMin || !data.salaryMax || Number(data.salaryMax) >= Number(data.salaryMin), {
    message: 'Lương tối đa phải lớn hơn hoặc bằng lương tối thiểu',
    path: ['salaryMax'],
  })
  .refine((data) => !data.salaryCurrency || data.salaryCurrency.trim().length === 3, {
    message: 'Mã tiền tệ gồm 3 ký tự, ví dụ VND',
    path: ['salaryCurrency'],
  })
  .refine((data) => !data.deadline || data.deadline >= TODAY_ISO, {
    message: 'Hạn nộp không được ở trong quá khứ',
    path: ['deadline'],
  })

type CreateJobFormValues = z.infer<typeof createJobSchema>

const EMPTY_VALUES: CreateJobFormValues = {
  title: '',
  description: '',
  requirements: '',
  benefits: '',
  categoryCode: null,
  locationCode: null,
  employmentType: '',
  workMode: '',
  salaryMin: '',
  salaryMax: '',
  salaryCurrency: '',
  deadline: '',
  subject: '',
  body: '',
  senderName: '',
  senderTitle: '',
  address: '',
}

// FR-H11 - buoc nhan dien theo khoa (khong theo chi so) de chuyen che do thu cong -> AI giu dung buoc dang dung (4c).
type StepKey = 'basic' | 'salary' | 'criteria' | 'questions' | 'template'

const STEP_FIELDS: Record<StepKey, (keyof CreateJobFormValues)[]> = {
  basic: ['title', 'description', 'requirements', 'benefits', 'categoryCode', 'locationCode', 'employmentType', 'workMode'],
  salary: ['salaryMin', 'salaryMax', 'salaryCurrency', 'deadline'],
  criteria: [],
  questions: [],
  template: ['subject', 'body', 'senderName', 'senderTitle', 'address'],
}

const MANUAL_STEPS: { key: StepKey; title: string }[] = [
  { key: 'basic', title: 'Thông tin cơ bản' },
  { key: 'salary', title: 'Lương & thời hạn' },
  { key: 'template', title: 'Mẫu giấy mời phỏng vấn' },
]

// FR-H11 UI.md 4c - che do AI: chen buoc 3 (tieu chi), 4 (cau hoi).
const AI_STEPS: { key: StepKey; title: string }[] = [
  { key: 'basic', title: AI_STEP_TITLES[0] },
  { key: 'salary', title: AI_STEP_TITLES[1] },
  { key: 'criteria', title: AI_STEP_TITLES[2] },
  { key: 'questions', title: AI_STEP_TITLES[3] },
  { key: 'template', title: AI_STEP_TITLES[4] },
]

// O nhap cua tung truong (focus khi bam ten truong o khoi "Can bo sung" hoac khi luu bi loi).
const FIELD_INPUT_IDS: Partial<Record<keyof CreateJobFormValues, string>> = {
  title: 'job-title',
  description: 'job-description',
  requirements: 'job-requirements',
  benefits: 'job-benefits',
  categoryCode: 'job-category',
  locationCode: 'job-location',
  employmentType: 'job-employment-type',
  workMode: 'job-work-mode',
  salaryMin: 'job-salary-min',
  salaryMax: 'job-salary-max',
  salaryCurrency: 'job-salary-currency',
  deadline: 'job-deadline',
  subject: 'tpl-subject',
  body: 'tpl-body',
  senderName: 'tpl-sender-name',
  senderTitle: 'tpl-sender-title',
  address: 'tpl-address',
}

// R-M - truong trong danh sach "Can bo sung" -> truong bieu mau; muc an khi truong da co gia tri (R-M4).
const MISSING_FIELD_TARGET: Record<JobDraftField, keyof CreateJobFormValues> = {
  TITLE: 'title',
  DESCRIPTION: 'description',
  CATEGORY: 'categoryCode',
  LOCATION: 'locationCode',
  EMPLOYMENT_TYPE: 'employmentType',
  WORK_MODE: 'workMode',
  SALARY: 'salaryMin',
  DEADLINE: 'deadline',
  REQUIREMENTS: 'requirements',
  BENEFITS: 'benefits',
}

function asText(value: string | null | undefined): string {
  return value ?? ''
}

function isFieldFilled(field: JobDraftField, values: CreateJobFormValues): boolean {
  if (field === 'SALARY') {
    return asText(values.salaryMin).trim() !== '' || asText(values.salaryMax).trim() !== ''
  }
  return asText(values[MISSING_FIELD_TARGET[field]]).trim() !== ''
}

// Gia tri "Dien vao bieu mau" ghi vao tung truong; truong AI tra null KHONG co mat (de trong, khong xoa chu HR - 4d).
function draftFormValues(job: JobDraft): Partial<Record<FillableField, string>> {
  const candidates: Record<FillableField, string | null> = {
    title: job.title,
    description: job.description,
    requirements: job.requirements,
    benefits: job.benefits,
    categoryCode: job.categoryCode,
    locationCode: job.locationCode,
    employmentType: job.employmentType,
    workMode: job.workMode,
    salaryMin: job.salaryMin == null ? null : String(job.salaryMin),
    salaryMax: job.salaryMax == null ? null : String(job.salaryMax),
    salaryCurrency: job.salaryCurrency,
    deadline: job.deadline,
  }
  const values: Partial<Record<FillableField, string>> = {}
  for (const field of Object.keys(candidates) as FillableField[]) {
    const value = candidates[field]
    if (value != null) {
      values[field] = value
    }
  }
  return values
}

function toUndef(value?: string): string | undefined {
  const trimmed = value?.trim()
  return trimmed ? trimmed : undefined
}

function toNumber(value?: string): number | undefined {
  const trimmed = value?.trim()
  return trimmed ? Number(trimmed) : undefined
}

function toPayload(values: CreateJobFormValues): JobCreateOwnerRequest {
  return {
    job: {
      title: values.title.trim(),
      description: values.description.trim(),
      requirements: toUndef(values.requirements),
      // Gui null tuong minh khi chua chon - KHONG gui "" (backend tra 400 INVALID_CATALOG_CODE).
      categoryCode: values.categoryCode,
      locationCode: values.locationCode,
      employmentType: toUndef(values.employmentType),
      workMode: toUndef(values.workMode),
      salaryMin: toNumber(values.salaryMin),
      salaryMax: toNumber(values.salaryMax),
      salaryCurrency: toUndef(values.salaryCurrency),
      deadline: toUndef(values.deadline),
    },
    interviewTemplate: {
      subject: values.subject.trim(),
      body: values.body.trim(),
      senderName: values.senderName.trim(),
      senderTitle: toUndef(values.senderTitle),
      address: toUndef(values.address),
    },
  }
}

interface SaveErrors {
  general: string | null
  // Theo so thu tu 0-based; -1 = loi tong trong so (RUBRIC_WEIGHT_EXCEEDED).
  criteria: Record<number, string>
  questions: Record<number, string>
}

const NO_SAVE_ERRORS: SaveErrors = { general: null, criteria: {}, questions: {} }

export function HrJobCreatePage() {
  const navigate = useNavigate()
  const createMutation = useCreateHrJobMutation()
  const saveMutation = useSaveJobDraftMutation()
  const catalogsQuery = useCatalogsQuery()
  const draft = useJobAiDraft()
  const [stepKey, setStepKey] = useState<StepKey>('basic')
  const [overwrite, setOverwrite] = useState<{
    edited: FillableField[]
    values: Partial<Record<FillableField, string>>
  } | null>(null)
  const [showCriteriaErrors, setShowCriteriaErrors] = useState(false)
  const [saveErrors, setSaveErrors] = useState<SaveErrors>(NO_SAVE_ERRORS)

  const {
    register,
    control,
    trigger,
    setValue,
    getValues,
    handleSubmit,
    formState: { errors, isSubmitting, touchedFields },
  } = useForm<CreateJobFormValues>({
    resolver: zodResolver(createJobSchema),
    defaultValues: EMPTY_VALUES,
    mode: 'onTouched',
  })
  const watched = useWatch({ control }) as CreateJobFormValues

  const aiMode = draft.aiMode
  const steps = aiMode ? AI_STEPS : MANUAL_STEPS
  const stepIndex = Math.max(
    steps.findIndex((s) => s.key === stepKey),
    0,
  )
  const currentKey = steps[stepIndex].key

  // Doi buoc roi focus o (khoi "Can bo sung", loi khi luu) - sau khi buoc moi da render.
  function goTo(key: StepKey, focusId?: string) {
    setStepKey(key)
    focusSoon(focusId)
  }

  function touchFields(fields: (keyof CreateJobFormValues)[]) {
    // trigger()/submit that bai chi BAO loi cho dung field duoc yeu cau (RHF scope theo ten
    // truong), nhung UI van dua vao touchedFields de quyet dinh hien do - "touch" thu cong o
    // day de loi cua field vua kiem tra hien ra ngay, khong doi field bi blur.
    fields.forEach((name) => setValue(name, getValues(name), { shouldTouch: true, shouldValidate: false }))
  }

  async function handleNext() {
    const fields = STEP_FIELDS[currentKey]
    const valid = fields.length === 0 || (await trigger(fields))
    if (valid) {
      setStepKey(steps[Math.min(stepIndex + 1, steps.length - 1)].key)
    } else {
      touchFields(fields)
    }
  }

  function handleBack() {
    setStepKey(steps[Math.max(stepIndex - 1, 0)].key)
  }

  const onSubmit = handleSubmit(
    async (values) => {
      try {
        const created = await createMutation.mutateAsync(toPayload(values))
        navigate(`/hr/jobs/${created.id}/edit?tab=rubric&created=1`)
      } catch {
        // createMutation.isError da phan anh loi nay ra UI ben duoi, khong can lam gi them.
      }
    },
    () => {
      // Submit that bai vi thieu du lieu o buoc cuoi (buoc 1, 2 da duoc gac khi "Tiep theo").
      // Touch toan bo field de loi hien ra dung cho lan co gang submit dau tien, khong dua vao
      // isSubmitted (co global, khong phan biet duoc HR da di qua buoc nao).
      touchFields(Object.keys(EMPTY_VALUES) as (keyof CreateJobFormValues)[])
    },
  )

  // ---- FR-H11 - "Dien vao bieu mau" (4d, 4f) ----

  // Truong HR da sua tay: khac gia tri AI da dien truoc do, hoac HR tu go truoc khi dien (4f). Truong ma gia tri moi
  // trung gia tri hien tai khong tinh.
  function editedFields(values: Partial<Record<FillableField, string>>): FillableField[] {
    const current = getValues()
    return (Object.keys(values) as FillableField[]).filter((field) => {
      const now = asText(current[field])
      if (now === values[field]) {
        return false
      }
      const previous = draft.aiFilled[field]
      return previous !== undefined ? now !== previous : now.trim() !== ''
    })
  }

  function applyFill(values: Partial<Record<FillableField, string>>) {
    for (const field of Object.keys(values) as FillableField[]) {
      setValue(field, values[field] as string, { shouldDirty: true })
    }
    draft.markJobFilled(values)
  }

  function handleFill() {
    if (!draft.jobResult) {
      return
    }
    const values = draftFormValues(draft.jobResult.data)
    const edited = editedFields(values)
    if (edited.length === 0) {
      applyFill(values)
    } else {
      setOverwrite({ edited, values })
    }
  }

  function fillUntouched() {
    if (!overwrite) {
      return
    }
    const values = { ...overwrite.values }
    overwrite.edited.forEach((field) => delete values[field])
    applyFill(values)
    setOverwrite(null)
  }

  function overwriteAll() {
    if (!overwrite) {
      return
    }
    applyFill(overwrite.values)
    setOverwrite(null)
  }

  // Dau "Do AI goi y": con khi gia tri truong van bang gia tri AI da dien.
  function aiMarkId(field: FillableField): string | undefined {
    const filled = draft.aiFilled[field]
    if (!aiMode || filled === undefined || asText(watched[field]) !== filled) {
      return undefined
    }
    return `${FIELD_INPUT_IDS[field]}-ai`
  }

  function fieldLabel(field: FillableField, htmlFor: string, text: string) {
    const markId = aiMarkId(field)
    return (
      <div className="flex flex-wrap items-center gap-x-2">
        <Label htmlFor={htmlFor}>{text}</Label>
        {markId && <AiSuggestedMark id={markId} />}
      </div>
    )
  }

  const missingItems = aiMode && draft.jobResult
    ? draft.jobResult.data.missingFields.filter((item) => !isFieldFilled(item.field, watched))
    : []

  function selectMissing(field: JobDraftField) {
    const target = MISSING_FIELD_TARGET[field]
    goTo(STEP_FIELDS.basic.includes(target) ? 'basic' : 'salary', FIELD_INPUT_IDS[target])
  }

  // ---- FR-H11 - "Luu nhap" (4h): kiem phia trinh duyet roi J2 ----

  function firstInvalidStep(formErrors: FieldErrors<CreateJobFormValues>): { key: StepKey; focusId?: string } | null {
    for (const step of AI_STEPS) {
      const field = STEP_FIELDS[step.key].find((name) => formErrors[name])
      if (field) {
        return { key: step.key, focusId: FIELD_INPUT_IDS[field] }
      }
    }
    return null
  }

  function criteriaProblem(): { focusId?: string } | null {
    const kept = draft.keptCriteria
    const bad = kept.find((c) => weightError(c.weight) || criterionNameError(c.name, c.id, kept))
    if (bad) {
      return { focusId: weightInputId(bad.id) }
    }
    return totalWeight(kept) > 100 ? {} : null
  }

  function applySaveError(error: unknown) {
    const message = extractErrorMessage(error, JOB_DRAFT_TEXT.saveFallbackError)
    const code = isAxiosError(error) ? (error.response?.data as { error?: unknown } | undefined)?.error : undefined
    const next: SaveErrors = { general: message, criteria: {}, questions: {} }
    const criterion = /^Tiêu chí (\d+): /.exec(message)
    const question = /^Câu hỏi (\d+): /.exec(message)
    if (criterion) {
      next.criteria[Number(criterion[1]) - 1] = message
      goTo('criteria')
    } else if (question) {
      next.questions[Number(question[1]) - 1] = message
      goTo('questions')
    } else if (code === 'RUBRIC_WEIGHT_EXCEEDED') {
      next.criteria[-1] = message
      goTo('criteria')
    }
    setSaveErrors(next)
  }

  const onSaveDraft = handleSubmit(
    async (values) => {
      setShowCriteriaErrors(true)
      setSaveErrors(NO_SAVE_ERRORS)
      const problem = criteriaProblem()
      if (problem) {
        goTo('criteria', problem.focusId)
        return
      }
      const base = toPayload(values)
      const tokens = draft.tokensForSave()
      const payload: JobAiDraftSaveRequest = {
        job: base.job,
        benefits: toUndef(values.benefits) ?? null,
        interviewTemplate: base.interviewTemplate,
        criteria: draft.keptCriteria.map((c) => ({
          name: c.name.trim(),
          description: c.description.trim() || null,
          weight: Number(c.weight),
        })),
        screeningQuestions: draft.keptQuestions.map((q) => ({
          questionType: q.questionType,
          questionText: q.questionText,
          options: q.questionType === 'SINGLE_CHOICE' ? q.options : undefined,
          required: q.required,
        })),
        // Token gui NGUYEN VAN (khong giai ma) - R-K4-2.
        jobToken: tokens.JOB,
        criteriaToken: tokens.CRITERIA,
        questionsToken: tokens.QUESTIONS,
      }
      try {
        const saved = await saveMutation.mutateAsync(payload)
        navigate(`/hr/jobs/${saved.id}/edit?tab=rubric&created=1`)
      } catch (error) {
        applySaveError(error)
      }
    },
    (formErrors) => {
      setShowCriteriaErrors(true)
      touchFields(Object.keys(EMPTY_VALUES) as (keyof CreateJobFormValues)[])
      // 4h - chuyen toi BUOC DAU TIEN co loi (theo thu tu 5 buoc), ke ca loi tieu chi o buoc 3.
      const target = firstInvalidStep(formErrors)
      const criteria = criteriaProblem()
      const stepOrder = (key: StepKey) => AI_STEPS.findIndex((s) => s.key === key)
      if (criteria && (!target || stepOrder('criteria') < stepOrder(target.key))) {
        goTo('criteria', criteria.focusId)
      } else if (target) {
        goTo(target.key, target.focusId)
      }
    },
  )

  const isLastStep = stepIndex === steps.length - 1

  return (
    <HrLayout title="Tạo tin tuyển dụng">
      <AiDraftInputPanel draft={draft} />

      <OverwriteFieldsDialog
        open={overwrite !== null}
        editedLabels={(overwrite?.edited ?? []).map((field) => FILLABLE_FIELD_LABELS[field])}
        onFillUntouched={fillUntouched}
        onOverwriteAll={overwriteAll}
        onCancel={() => setOverwrite(null)}
      />

      <form onSubmit={aiMode ? onSaveDraft : onSubmit} noValidate>
        <Card className="mx-auto max-w-2xl">
          <CardHeader>
            <CardTitle>Tạo tin tuyển dụng</CardTitle>
            {aiMode ? (
              <AiStepper steps={steps} stepIndex={stepIndex} />
            ) : (
              <div className="mt-2 flex items-center gap-2">
                {steps.map((s, index) => (
                  <div key={s.title} className="flex flex-1 items-center gap-2">
                    <div
                      className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-medium ${
                        index <= stepIndex ? 'bg-m3-primary text-m3-on-primary' : 'bg-m3-surface-container text-m3-on-surface'
                      }`}
                    >
                      {index + 1}
                    </div>
                    <span
                      className={`text-xs ${index === stepIndex ? 'font-medium text-m3-on-surface' : 'text-m3-on-surface-variant'}`}
                    >
                      {s.title}
                    </span>
                    {index < steps.length - 1 && <div className="h-px flex-1 bg-m3-surface-container-highest" />}
                  </div>
                ))}
              </div>
            )}
          </CardHeader>

          <CardContent className="flex flex-col gap-4">
            {aiMode && <MissingFieldsCard items={missingItems} onSelect={selectMissing} />}

            {currentKey === 'basic' && (
              <>
                {aiMode && (
                  <>
                    <PartStepStatus
                      part="JOB"
                      state={draft.parts.JOB}
                      disabled={draft.isGenerating}
                      onRetry={(parts) => void draft.generate(parts)}
                    />
                    {draft.jobResult && (
                      <PostingSuggestionBlock
                        draft={draft.jobResult.data}
                        filled={draft.jobFilled}
                        industries={catalogsQuery.data?.industries}
                        provinces={catalogsQuery.data?.provinces}
                        onFill={handleFill}
                        onRegenerate={() => void draft.generate(['JOB'])}
                        regenerateDisabled={draft.isGenerating}
                      />
                    )}
                  </>
                )}

                <div className="flex flex-col gap-1.5">
                  {fieldLabel('title', 'job-title', 'Tiêu đề tin tuyển dụng')}
                  <Input id="job-title" aria-describedby={aiMarkId('title')} {...register('title')} />
                  {errors.title && (touchedFields.title) && (
                    <p className="text-sm text-m3-error">{errors.title.message}</p>
                  )}
                </div>

                <div className="flex flex-col gap-1.5">
                  {fieldLabel('description', 'job-description', 'Mô tả công việc')}
                  <Textarea
                    id="job-description"
                    rows={5}
                    aria-describedby={aiMarkId('description')}
                    {...register('description')}
                  />
                  {errors.description && (touchedFields.description) && (
                    <p className="text-sm text-m3-error">{errors.description.message}</p>
                  )}
                </div>

                <div className="flex flex-col gap-1.5">
                  {fieldLabel('requirements', 'job-requirements', 'Yêu cầu ứng viên')}
                  <Textarea
                    id="job-requirements"
                    rows={4}
                    aria-describedby={aiMarkId('requirements')}
                    {...register('requirements')}
                  />
                </div>

                {aiMode && (
                  // FR-H11 R-P7 - o "Quyen loi" chi o che do AI; backend ghep vao cuoi mo ta khi luu.
                  <div className="flex flex-col gap-1.5">
                    {fieldLabel('benefits', 'job-benefits', JOB_DRAFT_TEXT.benefitsLabel)}
                    <Textarea
                      id="job-benefits"
                      rows={3}
                      aria-describedby={['job-benefits-helper', aiMarkId('benefits')].filter(Boolean).join(' ')}
                      {...register('benefits')}
                    />
                    <p id="job-benefits-helper" className="text-sm break-words text-m3-on-surface-variant">
                      {JOB_DRAFT_TEXT.benefitsHelper}
                    </p>
                  </div>
                )}

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="flex flex-col gap-1">
                    <Controller
                      control={control}
                      name="categoryCode"
                      render={({ field }) => (
                        <CatalogField
                          id="job-category"
                          label="Ngành nghề"
                          helperText={CATEGORY_HELPER_TEXT}
                          placeholder="Chọn ngành nghề"
                          searchPlaceholder="Tìm ngành nghề..."
                          value={field.value}
                          onChange={field.onChange}
                          items={catalogsQuery.data?.industries}
                          isLoading={catalogsQuery.isLoading}
                          isError={catalogsQuery.isError}
                          onRetry={() => catalogsQuery.refetch()}
                        />
                      )}
                    />
                    {aiMarkId('categoryCode') && <AiSuggestedMark id={aiMarkId('categoryCode')} />}
                  </div>
                  <div className="flex flex-col gap-1">
                    <Controller
                      control={control}
                      name="locationCode"
                      render={({ field }) => (
                        <CatalogField
                          id="job-location"
                          label="Tỉnh/thành"
                          helperText={LOCATION_HELPER_TEXT}
                          placeholder="Chọn tỉnh/thành"
                          searchPlaceholder="Tìm tỉnh/thành..."
                          value={field.value}
                          onChange={field.onChange}
                          items={catalogsQuery.data?.provinces}
                          isLoading={catalogsQuery.isLoading}
                          isError={catalogsQuery.isError}
                          onRetry={() => catalogsQuery.refetch()}
                        />
                      )}
                    />
                    {aiMarkId('locationCode') && <AiSuggestedMark id={aiMarkId('locationCode')} />}
                  </div>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    {fieldLabel('employmentType', 'job-employment-type', 'Loại hình làm việc')}
                    <Controller
                      control={control}
                      name="employmentType"
                      render={({ field }) => (
                        // Dot 6 (chore/hardening) - value={field.value} TRUC TIEP (khong "|| undefined"):
                        // defaultValues (EMPTY_VALUES) da dam bao field.value la chuoi '' ngay tu lan
                        // render DAU TIEN, khong bao gio la undefined - Radix Select coi '' la "chua
                        // chon gi" (khong khop SelectItem nao nen hien placeholder), hoan toan hop le
                        // lam gia tri controlled. "|| undefined" cu khien value chuyen tu undefined
                        // (chua chon) sang chuoi that (da chon) NGAY LUC nguoi dung chon lan dau - day
                        // moi la nguyen nhan that cua canh bao "changing an uncontrolled ... to be
                        // controlled" (khong phai do thieu default, xem CLAUDE.md/ROADMAP).
                        //
                        // FR-H11 (REQUIREMENT muc 0.a9) - Radix Select co the phat onValueChange('') khi gia
                        // tri doi bang code (setValue cua "Dien vao bieu mau") -> bo qua chuoi rong, khong
                        // xoa gia tri. Ap cho ca hai che do; o nay khong co lua chon "bo chon" nen khong mat
                        // thao tac nao cua nguoi dung.
                        <Select value={field.value} onValueChange={(value) => { if (value) field.onChange(value) }}>
                          <SelectTrigger
                            id="job-employment-type"
                            className="w-full"
                            aria-describedby={aiMarkId('employmentType')}
                          >
                            <SelectValue placeholder="Chọn loại hình" />
                          </SelectTrigger>
                          <SelectContent>
                            {EMPLOYMENT_TYPE_OPTIONS.map((opt) => (
                              <SelectItem key={opt} value={opt}>
                                {EMPLOYMENT_TYPE_LABELS[opt]}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      )}
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    {fieldLabel('workMode', 'job-work-mode', 'Hình thức làm việc')}
                    <Controller
                      control={control}
                      name="workMode"
                      render={({ field }) => (
                        // FR-H11 muc 0.a9 - cung ly do o "Loai hinh" ben tren.
                        <Select value={field.value} onValueChange={(value) => { if (value) field.onChange(value) }}>
                          <SelectTrigger id="job-work-mode" className="w-full" aria-describedby={aiMarkId('workMode')}>
                            <SelectValue placeholder="Chọn hình thức" />
                          </SelectTrigger>
                          <SelectContent>
                            {WORK_MODE_OPTIONS.map((opt) => (
                              <SelectItem key={opt} value={opt}>
                                {WORK_MODE_LABELS[opt]}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      )}
                    />
                  </div>
                </div>
              </>
            )}

            {currentKey === 'salary' && (
              <>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    {fieldLabel('salaryMin', 'job-salary-min', 'Lương tối thiểu')}
                    <Input
                      id="job-salary-min"
                      type="number"
                      min={0}
                      aria-describedby={aiMarkId('salaryMin')}
                      {...register('salaryMin')}
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    {fieldLabel('salaryMax', 'job-salary-max', 'Lương tối đa')}
                    <Input
                      id="job-salary-max"
                      type="number"
                      min={0}
                      aria-describedby={aiMarkId('salaryMax')}
                      {...register('salaryMax')}
                    />
                    {errors.salaryMax && (touchedFields.salaryMax) && (
                      <p className="text-sm text-m3-error">{errors.salaryMax.message}</p>
                    )}
                  </div>
                </div>

                <div className="flex flex-col gap-1.5">
                  {fieldLabel('salaryCurrency', 'job-salary-currency', 'Đơn vị tiền tệ')}
                  <Input
                    id="job-salary-currency"
                    placeholder="VND"
                    aria-describedby={aiMarkId('salaryCurrency')}
                    {...register('salaryCurrency')}
                  />
                  {errors.salaryCurrency && (touchedFields.salaryCurrency) && (
                    <p className="text-sm text-m3-error">{errors.salaryCurrency.message}</p>
                  )}
                </div>

                <div className="flex flex-col gap-1.5">
                  {fieldLabel('deadline', 'job-deadline', 'Hạn ứng tuyển')}
                  <Input
                    id="job-deadline"
                    type="date"
                    min={TODAY_ISO}
                    aria-describedby={aiMarkId('deadline')}
                    {...register('deadline')}
                  />
                  {errors.deadline && (touchedFields.deadline) && (
                    <p className="text-sm text-m3-error">{errors.deadline.message}</p>
                  )}
                </div>
              </>
            )}

            {currentKey === 'criteria' && (
              <CriteriaSuggestionStep
                draft={draft}
                showAllErrors={showCriteriaErrors}
                serverErrors={saveErrors.criteria}
              />
            )}

            {currentKey === 'questions' && (
              <ScreeningSuggestionStep draft={draft} serverErrors={saveErrors.questions} />
            )}

            {currentKey === 'template' && (
              <>
                {aiMode && <p className="text-sm text-m3-on-surface">{JOB_DRAFT_TEXT.templateIntro}</p>}
                <p className="text-sm text-m3-on-surface-variant">
                  Mẫu giấy mời phỏng vấn dùng chung cho mọi ứng viên của tin này. Ngày giờ phỏng vấn cụ thể sẽ do bạn
                  điền khi mời từng ứng viên sau này, không cấu hình ở đây.
                </p>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="tpl-subject">Tiêu đề thư mời</Label>
                  <Input id="tpl-subject" {...register('subject')} />
                  {errors.subject && (touchedFields.subject) && (
                    <p className="text-sm text-m3-error">{errors.subject.message}</p>
                  )}
                </div>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="tpl-body">Nội dung thư mời</Label>
                  <Textarea id="tpl-body" rows={6} {...register('body')} />
                  {errors.body && (touchedFields.body) && (
                    <p className="text-sm text-m3-error">{errors.body.message}</p>
                  )}
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="tpl-sender-name">Người gửi</Label>
                    <Input id="tpl-sender-name" {...register('senderName')} />
                    {errors.senderName && (touchedFields.senderName) && (
                      <p className="text-sm text-m3-error">{errors.senderName.message}</p>
                    )}
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="tpl-sender-title">Chức danh người gửi</Label>
                    <Input id="tpl-sender-title" {...register('senderTitle')} />
                  </div>
                </div>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="tpl-address">Địa chỉ phỏng vấn</Label>
                  <Textarea id="tpl-address" rows={2} {...register('address')} />
                </div>

                {aiMode && <p className="text-sm break-words text-m3-on-surface">{JOB_DRAFT_TEXT.beforeSave}</p>}
              </>
            )}

            {!aiMode && createMutation.isError && (
              <p className="text-sm text-m3-error">
                {extractErrorMessage(createMutation.error, 'Tạo tin tuyển dụng thất bại, vui lòng thử lại.')}
              </p>
            )}
            {aiMode && saveErrors.general && (
              <p role="alert" className="text-sm break-words text-m3-error">
                {saveErrors.general}
              </p>
            )}
          </CardContent>

          <CardFooter className="flex items-center justify-between gap-2">
            <Button
              type="button"
              variant="outline"
              className={aiMode ? 'min-h-12 flex-1 sm:min-h-10 sm:flex-none' : undefined}
              onClick={handleBack}
              disabled={stepIndex === 0}
            >
              Quay lại
            </Button>
            {isLastStep ? (
              // type="button" + goi onSubmit() thu cong - KHONG dung type="submit". Nut nay o
              // cung mot vi tri JSX voi nut "Tiep theo" (chi khac o step cuoi), va handleNext la
              // async: neu nut nay tung la type="submit", React co the doi thuoc tinh type ngay
              // GIUA luc trinh duyet dang xu ly click (mousedown/mouseup) cua lan bam "Tiep theo"
              // truoc do, khien form submit "chui" ngay khi vua sang buoc cuoi - day chinh la
              // nguyen nhan 3 dong do hien san o buoc 3 du chua go gi.
              aiMode ? (
                <Button
                  type="button"
                  className="min-h-12 flex-1 sm:min-h-10 sm:flex-none"
                  disabled={isSubmitting || saveMutation.isPending}
                  onClick={() => onSaveDraft()}
                >
                  {saveMutation.isPending ? JOB_DRAFT_TEXT.saving : JOB_DRAFT_TEXT.save}
                </Button>
              ) : (
                <Button type="button" disabled={isSubmitting || createMutation.isPending} onClick={() => onSubmit()}>
                  {createMutation.isPending ? 'Đang tạo...' : 'Tạo tin tuyển dụng'}
                </Button>
              )
            ) : (
              <Button
                type="button"
                className={aiMode ? 'min-h-12 flex-1 sm:min-h-10 sm:flex-none' : undefined}
                onClick={handleNext}
              >
                Tiếp theo
              </Button>
            )}
          </CardFooter>
        </Card>
      </form>
    </HrLayout>
  )
}

// FR-H11 UI.md 4j, muc 8 - stepper 5 buoc: compact chi "Buoc n/5" + ten buoc + cham; tu sm hien du 5 buoc (so tren,
// nhan duoi o medium; cung hang o expanded).
function AiStepper({ steps, stepIndex }: { steps: { key: StepKey; title: string }[]; stepIndex: number }) {
  return (
    <div className="mt-2">
      <div className="flex flex-col gap-1.5 sm:hidden">
        <p className="text-sm text-m3-on-surface-variant">{JOB_DRAFT_TEXT.compactStep(stepIndex + 1, steps.length)}</p>
        <p className="text-sm font-medium text-m3-on-surface">{steps[stepIndex].title}</p>
        <div className="flex items-center gap-1.5" aria-hidden="true">
          {steps.map((s, index) => (
            <span
              key={s.key}
              className={`h-2 w-2 rounded-full ${index <= stepIndex ? 'bg-m3-primary' : 'bg-m3-surface-container-highest'}`}
            />
          ))}
        </div>
      </div>
      <ol className="hidden items-start gap-2 sm:flex">
        {steps.map((s, index) => (
          <li
            key={s.key}
            aria-current={index === stepIndex ? 'step' : undefined}
            className="flex flex-1 flex-col items-center gap-1 text-center lg:flex-row lg:text-left"
          >
            <span
              className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-medium ${
                index <= stepIndex ? 'bg-m3-primary text-m3-on-primary' : 'bg-m3-surface-container text-m3-on-surface'
              }`}
            >
              {index + 1}
            </span>
            <span
              className={`text-xs break-words ${index === stepIndex ? 'font-medium text-m3-on-surface' : 'text-m3-on-surface-variant'}`}
            >
              {s.title}
            </span>
          </li>
        ))}
      </ol>
    </div>
  )
}

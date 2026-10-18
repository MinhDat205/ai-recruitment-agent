import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { AlertCircle, Info } from 'lucide-react'
import { useEffect, useId, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react'
import {
  Controller,
  FormProvider,
  get,
  useFieldArray,
  useForm,
  useFormContext,
  useFormState,
  useWatch,
  type FieldErrors,
} from 'react-hook-form'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { extractErrorMessage } from '../../lib/httpError'
import { SkillTagInput } from '../candidateProfile/SkillTagInput'
import { useCatalogsQuery } from '../catalog/queries'
import type { CatalogItem } from '../catalog/types'
import { getParsedResumeRequest } from '../resumes/api'
import { useResumesQuery } from '../resumes/queries'
import {
  CV_BUILDER_FIELD_LABELS,
  CV_BUILDER_LIMITS,
  CV_BUILDER_TEXT,
  EXCLUDED_INDUSTRY_CODE,
  cvBuilderCharCounter,
  cvBuilderTechnologyRemoveLabel,
} from './cvBuilderLabels'
import {
  CV_EMPTY_ERROR_KEY,
  EMPTY_CERTIFICATION,
  EMPTY_EDUCATION,
  EMPTY_EXPERIENCE,
  EMPTY_PROJECT,
  createCvBuilderSchema,
  nfcLength,
  type CvBuilderContentValues,
  type CvBuilderFormValues,
} from './cvBuilderSchema'
import { ListSection } from './ListSection'
import { MonthField } from './MonthField'
import { prefillFromParsed } from './prefillFromParsed'
import { RephraseAssist, type RephraseLock } from './RephraseAssist'
import { TemplatePicker } from './TemplatePicker'
import type { CvBuilderRequest } from './types'

export type CvBuilderMode = 'create' | 'edit'

const PROFILE_PATH = '/candidate/profile'
// R-F11 - ma loi 409 can them lien ket "Về Hồ sơ và CV" (UI.md muc 6).
const CONCURRENT_CREATE_ERROR = 'CV_BUILDER_CONCURRENT_CREATE'
// Lua chon "Khong chon nganh" cua Select (Radix Select khong cho SelectItem value rong).
const NO_INDUSTRY = '__none__'

const F = CV_BUILDER_FIELD_LABELS
const L = CV_BUILDER_LIMITS

function extractErrorCode(err: unknown): string | undefined {
  if (isAxiosError(err)) {
    const data = err.response?.data as { error?: unknown } | undefined
    if (data && typeof data.error === 'string') {
      return data.error
    }
  }
  return undefined
}

function errorAt(errors: FieldErrors<CvBuilderFormValues>, name: string): string | undefined {
  const message: unknown = get(errors, `${name}.message`)
  return typeof message === 'string' ? message : undefined
}

// Loi cua ca khoi danh sach: resolver dat o "<khoi>.root" khi khoi co them loi o muc, nguoc lai o "<khoi>".
function listErrorAt(errors: FieldErrors<CvBuilderFormValues>, block: string): string | undefined {
  return errorAt(errors, `${block}.root`) ?? errorAt(errors, block)
}

// R-E5 - form co it nhat mot o khac rong (chuoi co chu, "Đang làm" duoc tick, the ky nang/cong nghe).
function hasAnyContent(value: unknown): boolean {
  if (typeof value === 'string') {
    return value.trim().length > 0
  }
  if (typeof value === 'boolean') {
    return value
  }
  if (Array.isArray(value)) {
    return value.some(hasAnyContent)
  }
  if (value && typeof value === 'object') {
    return Object.values(value).some(hasAnyContent)
  }
  return false
}

// ---------------------------------------------------------------------------------------------------------------------
// O nhap: Label that, " *" bang chu + aria-required o o bat buoc, aria-invalid + aria-describedby tro toi goi y, bo dem,
// loi (UI.md muc 9). Loi duoi o: AlertCircle + chu text-m3-error, role="alert" (UI.md muc 5d).

interface FieldAria {
  id: string
  'aria-describedby'?: string
  'aria-invalid'?: true
  'aria-required'?: true
}

interface FieldProps {
  id: string
  label: string
  required?: boolean
  hint?: string
  hintIcon?: boolean
  counter?: { n: number; max: number }
  error?: string
  children: (aria: FieldAria) => ReactNode
}

function Field({ id, label, required, hint, hintIcon, counter, error, children }: FieldProps) {
  const hintId = `${id}-hint`
  const counterId = `${id}-counter`
  const errorId = `${id}-error`
  const describedBy = [hint ? hintId : null, counter ? counterId : null, error ? errorId : null]
    .filter((part) => part !== null)
    .join(' ')
  const over = counter !== undefined && counter.n > counter.max

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <Label htmlFor={id} className="text-m3-on-surface">
        {label}
        {required && ' *'}
      </Label>
      {children({
        id,
        'aria-describedby': describedBy || undefined,
        'aria-invalid': error ? true : undefined,
        'aria-required': required ? true : undefined,
      })}
      {hint && (
        <p id={hintId} className="flex items-start gap-1.5 text-sm text-m3-on-surface-variant">
          {hintIcon && <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />}
          {hint}
        </p>
      )}
      {counter && (
        <span id={counterId} className={`self-end text-sm ${over ? 'text-m3-error' : 'text-m3-on-surface-variant'}`}>
          {cvBuilderCharCounter(counter.n, counter.max)}
        </span>
      )}
      {error && <FieldError id={errorId} message={error} />}
    </div>
  )
}

function FieldError({ id, message }: { id?: string; message: string }) {
  return (
    <p id={id} role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-error">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
      {message}
    </p>
  )
}

const INPUT_CLASS = 'min-h-10'
const TEXTAREA_CLASS = 'min-h-24 break-words'

// Mot khoi (the) co section + aria-labelledby tieu de khoi (UI.md muc 9).
function SectionCard({ title, children }: { title: string; children: ReactNode }) {
  const headingId = useId()
  return (
    <Card>
      <section aria-labelledby={headingId} className="flex flex-col gap-4">
        <CardHeader>
          <CardTitle id={headingId} className="text-m3-title-md text-m3-on-surface">
            {title}
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">{children}</CardContent>
      </section>
    </Card>
  )
}

// ---------------------------------------------------------------------------------------------------------------------
// The "Điền sẵn" (R-E) - chi o che do tao. Chon mot CV DONE cua chinh minh, lay du lieu bang endpoint co san
// GET /candidates/resumes/{id}/parsed (R-E1); form co noi dung thi hoi xac nhan truoc khi thay TOAN BO (R-E5).

type PrefillStatus = 'idle' | 'running' | 'done' | 'failed'

function PrefillCard({
  industryCodes,
  hasContent,
  onApply,
}: {
  industryCodes: ReadonlySet<string> | null
  hasContent: () => boolean
  onApply: (content: CvBuilderContentValues) => void
}) {
  const titleId = useId()
  const { data: resumes, isLoading } = useResumesQuery()
  const doneResumes = (resumes ?? []).filter((resume) => resume.parseStatus === 'DONE')
  const [selectedId, setSelectedId] = useState('')
  const [status, setStatus] = useState<PrefillStatus>('idle')
  // Giu toi khi dien san thanh cong lan khac hoac roi trang (UI.md muc 6).
  const [dateNotice, setDateNotice] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const keepButtonRef = useRef<HTMLButtonElement>(null)
  const selected = doneResumes.find((resume) => resume.id === selectedId)

  async function runPrefill(resumeId: string) {
    setConfirmOpen(false)
    setStatus('running')
    try {
      const parsed = await getParsedResumeRequest(resumeId)
      if (parsed === null) {
        setStatus('failed')
        return
      }
      const result = prefillFromParsed(parsed.data, industryCodes)
      onApply(result.form)
      setDateNotice(result.droppedDates > 0)
      setStatus('done')
    } catch {
      setStatus('failed')
    }
  }

  function handlePrefill() {
    if (!selected || status === 'running') {
      return
    }
    if (hasContent()) {
      setConfirmOpen(true)
      return
    }
    void runPrefill(selected.id)
  }

  function renderBody() {
    if (isLoading) {
      return (
        <Select disabled>
          <SelectTrigger className="min-h-10 w-full" aria-labelledby={titleId}>
            <SelectValue placeholder={CV_BUILDER_TEXT.prefillLoading} />
          </SelectTrigger>
        </Select>
      )
    }
    if (doneResumes.length === 0) {
      return <p className="text-sm text-m3-on-surface-variant">{CV_BUILDER_TEXT.prefillNone}</p>
    }
    return (
      <div className="flex flex-col gap-2">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <Select value={selectedId} onValueChange={setSelectedId}>
            <SelectTrigger className="min-h-10 w-full min-w-0 sm:flex-1" aria-labelledby={titleId}>
              <SelectValue placeholder={CV_BUILDER_TEXT.prefillSelectPlaceholder} />
            </SelectTrigger>
            <SelectContent>
              {doneResumes.map((resume) => (
                <SelectItem key={resume.id} value={resume.id}>
                  {resume.versionLabel ? `${resume.fileName} · ${resume.versionLabel}` : resume.fileName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            type="button"
            variant="outline"
            className="min-h-10 w-full sm:w-auto"
            disabled={!selected || status === 'running'}
            onClick={handlePrefill}
          >
            {status === 'running' ? CV_BUILDER_TEXT.prefillRunning : CV_BUILDER_TEXT.prefillButton}
          </Button>
        </div>
        {status === 'failed' && <FieldError message={CV_BUILDER_TEXT.prefillFailed} />}
        {status === 'done' && (
          <p role="status" className="flex items-start gap-1.5 text-sm text-m3-on-surface-variant">
            <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            {CV_BUILDER_TEXT.prefillDone}
          </p>
        )}
      </div>
    )
  }

  return (
    <Card>
      <section aria-labelledby={titleId} className="flex flex-col gap-4">
        <CardHeader>
          <CardTitle id={titleId} className="text-m3-title-md text-m3-on-surface">
            {CV_BUILDER_TEXT.prefillTitle}
          </CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {dateNotice && (
            <p className="flex items-start gap-1.5 text-sm text-m3-on-surface">
              <Info className="mt-0.5 h-4 w-4 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
              {CV_BUILDER_TEXT.prefillDateNotice}
            </p>
          )}
          {renderBody()}
        </CardContent>
      </section>

      {/* R-E5 - focus vao "Giữ nội dung hiện tại" khi mo (lua chon khong mat du lieu); Esc / bam ra ngoai = giu. */}
      <Dialog open={confirmOpen} onOpenChange={(next) => !next && setConfirmOpen(false)}>
        <DialogContent
          showCloseButton={false}
          className="sm:max-w-md"
          onOpenAutoFocus={(event) => {
            event.preventDefault()
            keepButtonRef.current?.focus()
          }}
        >
          <DialogHeader>
            <DialogTitle>{CV_BUILDER_TEXT.prefillButton}</DialogTitle>
            <DialogDescription className="text-m3-on-surface">{CV_BUILDER_TEXT.prefillConfirm}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button ref={keepButtonRef} type="button" variant="outline" onClick={() => setConfirmOpen(false)}>
              {CV_BUILDER_TEXT.prefillConfirmCancel}
            </Button>
            <Button type="button" onClick={() => selected && void runPrefill(selected.id)}>
              {CV_BUILDER_TEXT.prefillConfirmOk}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}

// ---------------------------------------------------------------------------------------------------------------------
// Muc cua cac khoi danh sach. Thu tu o trong muc = thu tu R-F1 (focus o loi dau tien theo thu tu dang ky).

function ExperienceItem({ index, ownerKey, idPrefix, lock }: ItemProps & { ownerKey: string; lock: RephraseLock }) {
  const { register, control } = useFormContext<CvBuilderFormValues>()
  const { errors } = useFormState({ control })
  const description = useWatch({ control, name: `experience.${index}.description` }) ?? ''
  const id = (key: string) => `${idPrefix}-experience-${index}-${key}`
  const path = (key: string) => `experience.${index}.${key}`

  return (
    <div className="flex flex-col gap-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field id={id('company')} label={F.company} required error={errorAt(errors, path('company'))}>
          {(aria) => <Input {...register(`experience.${index}.company`)} {...aria} className={INPUT_CLASS} />}
        </Field>
        <Field id={id('title')} label={F.experienceTitle} error={errorAt(errors, path('title'))}>
          {(aria) => <Input {...register(`experience.${index}.title`)} {...aria} className={INPUT_CLASS} />}
        </Field>
        <Field
          id={id('startDate')}
          label={F.startDate}
          hint={CV_BUILDER_TEXT.dateHint}
          error={errorAt(errors, path('startDate'))}
        >
          {(aria) => <MonthField {...register(`experience.${index}.startDate`)} {...aria} />}
        </Field>
        <Field id={id('endDate')} label={F.endDate} hint={CV_BUILDER_TEXT.dateHint} error={errorAt(errors, path('endDate'))}>
          {(aria) => <MonthField {...register(`experience.${index}.endDate`)} {...aria} />}
        </Field>
      </div>
      <Controller
        control={control}
        name={`experience.${index}.current`}
        render={({ field }) => (
          <div className="flex min-h-10 items-center gap-2">
            <Checkbox
              id={id('current')}
              ref={field.ref}
              checked={field.value}
              onCheckedChange={(checked) => field.onChange(checked === true)}
              onBlur={field.onBlur}
            />
            <Label htmlFor={id('current')} className="text-m3-on-surface">
              {CV_BUILDER_TEXT.currentLabel}
            </Label>
          </div>
        )}
      />
      <Field
        id={id('description')}
        label={F.description}
        counter={{ n: nfcLength(description), max: L.description }}
        error={errorAt(errors, path('description'))}
      >
        {(aria) => <Textarea {...register(`experience.${index}.description`)} {...aria} className={TEXTAREA_CLASS} />}
      </Field>
      <RephraseAssist name={`experience.${index}.description`} kind="EXPERIENCE" ownerKey={ownerKey} lock={lock} />
    </div>
  )
}

interface ItemProps {
  index: number
  idPrefix: string
}

function EducationItem({ index, idPrefix }: ItemProps) {
  const { register, control } = useFormContext<CvBuilderFormValues>()
  const { errors } = useFormState({ control })
  const id = (key: string) => `${idPrefix}-education-${index}-${key}`
  const path = (key: string) => `education.${index}.${key}`

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <div className="sm:col-span-2">
        <Field id={id('school')} label={F.school} required error={errorAt(errors, path('school'))}>
          {(aria) => <Input {...register(`education.${index}.school`)} {...aria} className={INPUT_CLASS} />}
        </Field>
      </div>
      <Field id={id('degree')} label={F.degree} error={errorAt(errors, path('degree'))}>
        {(aria) => <Input {...register(`education.${index}.degree`)} {...aria} className={INPUT_CLASS} />}
      </Field>
      <Field id={id('major')} label={F.major} error={errorAt(errors, path('major'))}>
        {(aria) => <Input {...register(`education.${index}.major`)} {...aria} className={INPUT_CLASS} />}
      </Field>
      <Field id={id('startDate')} label={F.startDate} hint={CV_BUILDER_TEXT.dateHint} error={errorAt(errors, path('startDate'))}>
        {(aria) => <MonthField {...register(`education.${index}.startDate`)} {...aria} />}
      </Field>
      <Field id={id('endDate')} label={F.endDate} hint={CV_BUILDER_TEXT.dateHint} error={errorAt(errors, path('endDate'))}>
        {(aria) => <MonthField {...register(`education.${index}.endDate`)} {...aria} />}
      </Field>
      <Field id={id('gpa')} label={F.gpa} error={errorAt(errors, path('gpa'))}>
        {(aria) => (
          <Input
            {...register(`education.${index}.gpa`)}
            {...aria}
            inputMode="decimal"
            autoComplete="off"
            className={INPUT_CLASS}
          />
        )}
      </Field>
    </div>
  )
}

function ProjectItem({ index, ownerKey, idPrefix, lock }: ItemProps & { ownerKey: string; lock: RephraseLock }) {
  const { register, control } = useFormContext<CvBuilderFormValues>()
  const { errors } = useFormState({ control })
  const description = useWatch({ control, name: `projects.${index}.description` }) ?? ''
  const id = (key: string) => `${idPrefix}-projects-${index}-${key}`
  const path = (key: string) => `projects.${index}.${key}`

  return (
    <div className="flex flex-col gap-4">
      <Field id={id('name')} label={F.projectName} required error={errorAt(errors, path('name'))}>
        {(aria) => <Input {...register(`projects.${index}.name`)} {...aria} className={INPUT_CLASS} />}
      </Field>
      <Field
        id={id('description')}
        label={F.description}
        counter={{ n: nfcLength(description), max: L.description }}
        error={errorAt(errors, path('description'))}
      >
        {(aria) => <Textarea {...register(`projects.${index}.description`)} {...aria} className={TEXTAREA_CLASS} />}
      </Field>
      <RephraseAssist name={`projects.${index}.description`} kind="PROJECT" ownerKey={ownerKey} lock={lock} />
      <Controller
        control={control}
        name={`projects.${index}.technologies`}
        render={({ field }) => (
          <Field
            id={id('technologies')}
            label={F.technologies}
            counter={{ n: field.value.length, max: L.technologiesItems }}
            error={errorAt(errors, path('technologies'))}
          >
            {(aria) => (
              <SkillTagInput
                id={aria.id}
                value={field.value}
                onChange={field.onChange}
                max={L.technologiesItems}
                placeholder={CV_BUILDER_TEXT.technologiesPlaceholder}
                maxReachedPlaceholder={CV_BUILDER_TEXT.technologiesMaxReached}
                describedBy={aria['aria-describedby']}
                inputAriaLabel={CV_BUILDER_TEXT.technologiesInputLabel}
                removeAriaLabel={cvBuilderTechnologyRemoveLabel}
              />
            )}
          </Field>
        )}
      />
    </div>
  )
}

function CertificationItem({ index, idPrefix }: ItemProps) {
  const { register, control } = useFormContext<CvBuilderFormValues>()
  const { errors } = useFormState({ control })
  const id = (key: string) => `${idPrefix}-certifications-${index}-${key}`
  const path = (key: string) => `certifications.${index}.${key}`

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <div className="sm:col-span-2">
        <Field id={id('name')} label={F.certificationName} required error={errorAt(errors, path('name'))}>
          {(aria) => <Input {...register(`certifications.${index}.name`)} {...aria} className={INPUT_CLASS} />}
        </Field>
      </div>
      <Field id={id('issuer')} label={F.issuer} error={errorAt(errors, path('issuer'))}>
        {(aria) => <Input {...register(`certifications.${index}.issuer`)} {...aria} className={INPUT_CLASS} />}
      </Field>
      <Field
        id={id('issueDate')}
        label={F.issueDate}
        hint={CV_BUILDER_TEXT.dateHint}
        error={errorAt(errors, path('issueDate'))}
      >
        {(aria) => <MonthField {...register(`certifications.${index}.issueDate`)} {...aria} />}
      </Field>
    </div>
  )
}

// ---------------------------------------------------------------------------------------------------------------------

interface ActionError {
  message: string
  code: string | undefined
}

interface CvBuilderFormViewProps {
  mode: CvBuilderMode
  initial: CvBuilderFormValues
  // Goi B1 voi than request da chuan hoa; nem loi -> form hien cau backend o thanh thao tac, giu nguyen du lieu.
  onSave: (request: CvBuilderRequest) => Promise<void>
}

// FR-U12 UI.md muc 4a, 5a, 6, 9 - toan bo form tao CV. Thu tu khoi tren trang = thu tu in cua PDF (R-P6) sau hai the
// "Điền sẵn", "Mẫu trình bày". Form chi nam trong state React - KHONG luu nhap vao bo nho trinh duyet hay server (UI.md
// muc 10). Thanh thao tac o CUOI form, trong luong trang, khong ghim vi tri tren man hinh (L1).
export function CvBuilderFormView({ mode, initial, onSave }: CvBuilderFormViewProps) {
  const idPrefix = useId()
  const catalogsQuery = useCatalogsQuery()
  const catalogs = catalogsQuery.data
  // Danh muc C05 tru "Ngành khác" - backend tu choi ma nay (R-F5).
  const industries: CatalogItem[] = useMemo(
    () => (catalogs?.industries ?? []).filter((item) => item.code !== EXCLUDED_INDUSTRY_CODE),
    [catalogs],
  )
  const industryCodes = useMemo(
    () => (catalogs ? new Set(catalogs.industries.map((item) => item.code)) : null),
    [catalogs],
  )
  const schema = useMemo(() => createCvBuilderSchema(industryCodes), [industryCodes])

  const methods = useForm<CvBuilderFormValues, unknown, CvBuilderRequest>({
    resolver: zodResolver(schema),
    defaultValues: initial,
  })
  const { control, register, handleSubmit, getValues, reset, formState } = methods
  const { errors } = formState

  const education = useFieldArray({ control, name: 'education' })
  const experience = useFieldArray({ control, name: 'experience' })
  const projects = useFieldArray({ control, name: 'projects' })
  const certifications = useFieldArray({ control, name: 'certifications' })

  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState<ActionError | null>(null)
  // Tang moi lan can cuon toi + focus khoi loi cua thanh thao tac (sau lan render hien loi).
  const [actionFocusSeq, setActionFocusSeq] = useState(0)
  const actionErrorRef = useRef<HTMLDivElement>(null)

  // Ca trang chi mot request goi y cung luc (UI.md muc 5a quy tac 3).
  const [rephraseOwner, setRephraseOwner] = useState<string | null>(null)
  const rephraseLock: RephraseLock = {
    owner: rephraseOwner,
    acquire: (owner) => setRephraseOwner(owner),
    release: (owner) => setRephraseOwner((current) => (current === owner ? null : current)),
  }

  useEffect(() => {
    if (actionFocusSeq === 0) {
      return
    }
    const block = actionErrorRef.current
    block?.scrollIntoView({ block: 'center' })
    block?.focus()
  }, [actionFocusSeq])

  async function onValid(request: CvBuilderRequest) {
    setSaving(true)
    try {
      await onSave(request)
    } catch (err) {
      // Loi server (400/409/500): cau message cua backend, khong xoa du lieu form (UI.md muc 6).
      setActionError({
        message: extractErrorMessage(err, CV_BUILDER_TEXT.saveFailedFallback),
        code: extractErrorCode(err),
      })
      setActionFocusSeq((seq) => seq + 1)
    } finally {
      setSaving(false)
    }
  }

  function onInvalid(invalid: FieldErrors<CvBuilderFormValues>) {
    // react-hook-form tu focus o loi dau tien; chi co loi R-F10 (khong o nao sai) -> focus khoi loi thanh thao tac.
    const onlyEmpty = Object.keys(invalid).every((key) => key === CV_EMPTY_ERROR_KEY)
    if (onlyEmpty) {
      setActionFocusSeq((seq) => seq + 1)
    }
  }

  function handleFormSubmit(event: FormEvent<HTMLFormElement>) {
    setActionError(null)
    void handleSubmit(onValid, onInvalid)(event)
  }

  // R-E4 - mau va ten phien ban giu nhu dang chon; keepDefaultValues de form dien san van tinh la "da sua".
  function applyPrefill(content: CvBuilderContentValues) {
    const { template, versionLabel } = getValues()
    reset({ template, versionLabel, ...content }, { keepDefaultValues: true })
    setActionError(null)
  }

  // Mau va ten phien ban khong tinh (dien san khong thay hai o nay - R-E4).
  function hasContent(): boolean {
    const values = getValues()
    const content: CvBuilderContentValues = {
      contact: values.contact,
      currentTitle: values.currentTitle,
      locationText: values.locationText,
      industryCode: values.industryCode,
      education: values.education,
      experience: values.experience,
      skills: values.skills,
      certifications: values.certifications,
      projects: values.projects,
    }
    return hasAnyContent(content)
  }

  const emptyError = errorAt(errors, CV_EMPTY_ERROR_KEY)
  const actionMessage = actionError?.message ?? emptyError
  const skillsError = errorAt(errors, 'skills')

  return (
    <FormProvider {...methods}>
      <form noValidate onSubmit={handleFormSubmit} className="flex flex-col gap-4">
        {mode === 'create' && (
          <PrefillCard industryCodes={industryCodes} hasContent={hasContent} onApply={applyPrefill} />
        )}

        <Card>
          <CardContent>
            <Controller
              control={control}
              name="template"
              render={({ field }) => <TemplatePicker value={field.value} onChange={field.onChange} />}
            />
          </CardContent>
        </Card>

        <SectionCard title={CV_BUILDER_TEXT.sectionContact}>
          <Field
            id={`${idPrefix}-fullName`}
            label={F.fullName}
            required
            error={errorAt(errors, 'contact.fullName')}
          >
            {(aria) => <Input {...register('contact.fullName')} {...aria} autoComplete="name" className={INPUT_CLASS} />}
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field id={`${idPrefix}-email`} label={F.email} error={errorAt(errors, 'contact.email')}>
              {(aria) => (
                <Input
                  {...register('contact.email')}
                  {...aria}
                  type="email"
                  autoComplete="email"
                  className={INPUT_CLASS}
                />
              )}
            </Field>
            <Field id={`${idPrefix}-phone`} label={F.phone} error={errorAt(errors, 'contact.phone')}>
              {(aria) => (
                <Input {...register('contact.phone')} {...aria} type="tel" autoComplete="tel" className={INPUT_CLASS} />
              )}
            </Field>
          </div>
          <Field id={`${idPrefix}-address`} label={F.address} error={errorAt(errors, 'contact.address')}>
            {(aria) => <Input {...register('contact.address')} {...aria} className={INPUT_CLASS} />}
          </Field>
          <Field id={`${idPrefix}-linkedin`} label={F.linkedin} error={errorAt(errors, 'contact.linkedin')}>
            {(aria) => <Input {...register('contact.linkedin')} {...aria} type="url" className={INPUT_CLASS} />}
          </Field>
        </SectionCard>

        <SectionCard title={CV_BUILDER_TEXT.sectionGeneral}>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field id={`${idPrefix}-currentTitle`} label={F.currentTitle} error={errorAt(errors, 'currentTitle')}>
              {(aria) => <Input {...register('currentTitle')} {...aria} className={INPUT_CLASS} />}
            </Field>
            <Field
              id={`${idPrefix}-locationText`}
              label={F.locationText}
              hint={CV_BUILDER_TEXT.locationHint}
              error={errorAt(errors, 'locationText')}
            >
              {(aria) => <Input {...register('locationText')} {...aria} className={INPUT_CLASS} />}
            </Field>
          </div>
          <Controller
            control={control}
            name="industryCode"
            render={({ field }) => (
              <Field
                id={`${idPrefix}-industryCode`}
                label={F.industryCode}
                hint={CV_BUILDER_TEXT.industryHint}
                hintIcon
                error={errorAt(errors, 'industryCode')}
              >
                {(aria) => (
                  <Select
                    value={field.value}
                    onValueChange={(next) => field.onChange(next === NO_INDUSTRY ? '' : next)}
                    disabled={!catalogs}
                  >
                    <SelectTrigger ref={field.ref} {...aria} className="min-h-10 w-full">
                      <SelectValue placeholder={CV_BUILDER_TEXT.industryPlaceholder} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NO_INDUSTRY}>{CV_BUILDER_TEXT.industryNone}</SelectItem>
                      {industries.map((item) => (
                        <SelectItem key={item.code} value={item.code}>
                          {item.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              </Field>
            )}
          />
        </SectionCard>

        <ListSection
          block="experience"
          title={CV_BUILDER_TEXT.sectionExperience}
          itemIds={experience.fields.map((item) => item.id)}
          max={L.experienceItems}
          addLabel={CV_BUILDER_TEXT.addExperience}
          emptyText={CV_BUILDER_TEXT.emptyExperience}
          error={listErrorAt(errors, 'experience')}
          onAdd={() => experience.append({ ...EMPTY_EXPERIENCE })}
          onMove={experience.move}
          onRemove={experience.remove}
          renderItem={(index) => (
            <ExperienceItem
              index={index}
              idPrefix={idPrefix}
              ownerKey={experience.fields[index].id}
              lock={rephraseLock}
            />
          )}
        />

        <ListSection
          block="education"
          title={CV_BUILDER_TEXT.sectionEducation}
          itemIds={education.fields.map((item) => item.id)}
          max={L.educationItems}
          addLabel={CV_BUILDER_TEXT.addEducation}
          emptyText={CV_BUILDER_TEXT.emptyEducation}
          error={listErrorAt(errors, 'education')}
          onAdd={() => education.append({ ...EMPTY_EDUCATION })}
          onMove={education.move}
          onRemove={education.remove}
          renderItem={(index) => <EducationItem index={index} idPrefix={idPrefix} />}
        />

        <ListSection
          block="projects"
          title={CV_BUILDER_TEXT.sectionProjects}
          itemIds={projects.fields.map((item) => item.id)}
          max={L.projectsItems}
          addLabel={CV_BUILDER_TEXT.addProject}
          emptyText={CV_BUILDER_TEXT.emptyProjects}
          error={listErrorAt(errors, 'projects')}
          onAdd={() => projects.append({ ...EMPTY_PROJECT, technologies: [] })}
          onMove={projects.move}
          onRemove={projects.remove}
          renderItem={(index) => (
            <ProjectItem index={index} idPrefix={idPrefix} ownerKey={projects.fields[index].id} lock={rephraseLock} />
          )}
        />

        <SectionCard title={CV_BUILDER_TEXT.sectionSkills}>
          <Controller
            control={control}
            name="skills"
            render={({ field }) => (
              <div className="flex flex-col gap-1.5">
                {field.value.length === 0 && (
                  <p className="text-sm text-m3-on-surface-variant">{CV_BUILDER_TEXT.emptySkills}</p>
                )}
                <SkillTagInput
                  id={`${idPrefix}-skills`}
                  value={field.value}
                  onChange={field.onChange}
                  max={L.skillsItems}
                  placeholder={CV_BUILDER_TEXT.skillsPlaceholder}
                  maxReachedPlaceholder={CV_BUILDER_TEXT.skillsMaxReached}
                  describedBy={`${idPrefix}-skills-counter${skillsError ? ` ${idPrefix}-skills-error` : ''}`}
                />
                <span id={`${idPrefix}-skills-counter`} className="self-end text-sm text-m3-on-surface-variant">
                  {cvBuilderCharCounter(field.value.length, L.skillsItems)}
                </span>
                {skillsError && <FieldError id={`${idPrefix}-skills-error`} message={skillsError} />}
              </div>
            )}
          />
        </SectionCard>

        <ListSection
          block="certifications"
          title={CV_BUILDER_TEXT.sectionCertifications}
          itemIds={certifications.fields.map((item) => item.id)}
          max={L.certificationsItems}
          addLabel={CV_BUILDER_TEXT.addCertification}
          emptyText={CV_BUILDER_TEXT.emptyCertifications}
          error={listErrorAt(errors, 'certifications')}
          onAdd={() => certifications.append({ ...EMPTY_CERTIFICATION })}
          onMove={certifications.move}
          onRemove={certifications.remove}
          renderItem={(index) => <CertificationItem index={index} idPrefix={idPrefix} />}
        />

        <SectionCard title={CV_BUILDER_TEXT.sectionVersion}>
          <Field id={`${idPrefix}-versionLabel`} label={F.versionLabel} error={errorAt(errors, 'versionLabel')}>
            {(aria) => (
              <Input
                {...register('versionLabel')}
                {...aria}
                placeholder={CV_BUILDER_TEXT.versionPlaceholder}
                className={INPUT_CLASS}
              />
            )}
          </Field>
        </SectionCard>

        {/* Thanh thao tac (UI.md muc 5d, L1): cuoi form, trong luong trang. Loi server/R-F10 ben trai nut, tren nen trang
            -> chu text-m3-on-surface + icon m3-error. */}
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          {actionMessage && (
            <div
              ref={actionErrorRef}
              role="alert"
              tabIndex={-1}
              className="flex flex-1 flex-col gap-1 text-sm break-words text-m3-on-surface outline-none"
            >
              <p className="flex items-start gap-1.5">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
                {actionMessage}
              </p>
              {actionError?.code === CONCURRENT_CREATE_ERROR && (
                <Link to={PROFILE_PATH} className="ml-5.5 w-fit text-m3-on-primary-container hover:underline">
                  {CV_BUILDER_TEXT.backToProfile}
                </Link>
              )}
            </div>
          )}
          <div className="flex flex-col gap-3 sm:ml-auto sm:flex-row">
            <Button type="submit" className="min-h-10 w-full sm:w-auto" disabled={saving}>
              {saving ? CV_BUILDER_TEXT.saving : mode === 'create' ? CV_BUILDER_TEXT.saveCreate : CV_BUILDER_TEXT.saveEdit}
            </Button>
          </div>
        </div>
      </form>
    </FormProvider>
  )
}

import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { useEffect, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { AlertTriangle } from 'lucide-react'
import { HrLayout } from '../components/layout/HrLayout'
import { useCatalogsQuery } from '../features/catalog/queries'
import { CATEGORY_HELPER_TEXT, LOCATION_HELPER_TEXT, isCatalogComplete } from '../features/jobs/catalogDisplay'
import { CatalogField } from '../features/jobs/CatalogField'
import { JobStatusBadge } from '../features/jobs/JobStatusBadge'
import { RubricTab } from '../features/rubric/RubricTab'
import { ApplicationsTab } from '../features/scoring/ApplicationsTab'
import { EMPLOYMENT_TYPE_LABELS, EMPLOYMENT_TYPE_OPTIONS, WORK_MODE_LABELS, WORK_MODE_OPTIONS } from '../features/jobs/jobLabels'
import {
  useHrJobQuery,
  useInterviewTemplateQuery,
  useUpdateHrJobMutation,
  useUpdateInterviewTemplateMutation,
} from '../features/jobs/ownerQueries'
import type { InterviewTemplateOwnerRequest, JobOwnerRequest } from '../features/jobs/ownerTypes'

const SAVE_SUCCESS_TIMEOUT_MS = 4000

function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

function toUndef(value?: string): string | undefined {
  const trimmed = value?.trim()
  return trimmed ? trimmed : undefined
}

function toNumber(value?: string): number | undefined {
  const trimmed = value?.trim()
  return trimmed ? Number(trimmed) : undefined
}

// ---- Tab "Thong tin tin tuyen dung" — PUT /hr/jobs/{id}, JobRequest phang, khong dung template ----

const jobInfoSchema = z
  .object({
    title: z.string().trim().min(1, 'Vui lòng nhập tiêu đề').max(200, 'Tối đa 200 ký tự'),
    description: z.string().trim().min(1, 'Vui lòng nhập mô tả công việc'),
    requirements: z.string().optional(),
    // FR-C05: ma danh muc, null = chua chon.
    categoryCode: z.string().nullable(),
    locationCode: z.string().nullable(),
    employmentType: z.string().optional(),
    workMode: z.string().optional(),
    salaryMin: z.string().optional(),
    salaryMax: z.string().optional(),
    salaryCurrency: z.string().optional(),
    deadline: z.string().optional(),
  })
  .refine((data) => !data.salaryMin || !data.salaryMax || Number(data.salaryMax) >= Number(data.salaryMin), {
    message: 'Lương tối đa phải lớn hơn hoặc bằng lương tối thiểu',
    path: ['salaryMax'],
  })
  .refine((data) => !data.salaryCurrency || data.salaryCurrency.trim().length === 3, {
    message: 'Mã tiền tệ gồm 3 ký tự, ví dụ VND',
    path: ['salaryCurrency'],
  })

type JobInfoFormValues = z.infer<typeof jobInfoSchema>

function toJobPayload(values: JobInfoFormValues): JobOwnerRequest {
  return {
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
  }
}

function JobInfoTab({ jobId }: { jobId: string }) {
  const { data: job, isLoading, isError } = useHrJobQuery(jobId)
  const updateMutation = useUpdateHrJobMutation(jobId)
  const catalogsQuery = useCatalogsQuery()
  const [showSuccess, setShowSuccess] = useState(false)

  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<JobInfoFormValues>({
    resolver: zodResolver(jobInfoSchema),
    // Dot 6 (chore/hardening) - defaultValues THEM MOI, dung song song voi values (khong thay the):
    // truoc day chi co "values" (RHF sync lai khi job tai xong QUA MOT useEffect NOI BO, chay SAU
    // khi render da commit) - trong khoang thoi gian giua lan render DAU TIEN cua Select (ngay khi
    // job vua tai xong, thoat khoi nhanh isLoading) va luc effect do kip chay, field.value cho
    // employmentType/workMode VAN la undefined (RHF chua co defaultValues nao de dung tam) - Select
    // nhan value={undefined} luc mount, roi chuyen sang chuoi that ngay sau do khi effect chay xong
    // -> dung LA canh bao "changing an uncontrolled ... to controlled". defaultValues cung chuoi
    // rong nhu EMPTY_VALUES cua HrJobCreatePage dam bao field.value la '' (KHONG PHAI undefined)
    // tu chinh lan render dau tien, xoa nguyen nhan gay canh bao. Cac field Input dung register()
    // it lo ro trieu chung tuong tu (native input dung defaultValue+register khac co che), nhung
    // van dong bo cho nhat quan.
    defaultValues: {
      title: '',
      description: '',
      requirements: '',
      categoryCode: null,
      locationCode: null,
      employmentType: '',
      workMode: '',
      salaryMin: '',
      salaryMax: '',
      salaryCurrency: '',
      deadline: '',
    },
    values: job
      ? {
          title: job.title,
          description: job.description,
          requirements: job.requirements ?? '',
          categoryCode: job.categoryCode,
          locationCode: job.locationCode,
          employmentType: job.employmentType ?? '',
          workMode: job.workMode ?? '',
          salaryMin: job.salaryMin != null ? String(job.salaryMin) : '',
          salaryMax: job.salaryMax != null ? String(job.salaryMax) : '',
          salaryCurrency: job.salaryCurrency ?? '',
          deadline: job.deadline ?? '',
        }
      : undefined,
  })

  useEffect(() => {
    if (!showSuccess) return
    const timer = setTimeout(() => setShowSuccess(false), SAVE_SUCCESS_TIMEOUT_MS)
    return () => clearTimeout(timer)
  }, [showSuccess])

  const onSubmit = handleSubmit(async (values) => {
    setShowSuccess(false)
    try {
      await updateMutation.mutateAsync(toJobPayload(values))
      setShowSuccess(true)
    } catch {
      // updateMutation.isError da phan anh loi nay ra UI ben duoi, khong can lam gi them.
    }
  })

  if (isLoading) {
    return <p className="p-6 text-sm text-ink-muted">Đang tải...</p>
  }
  if (isError || !job) {
    return <p className="p-6 text-sm text-danger">Không tải được tin tuyển dụng, vui lòng thử lại.</p>
  }

  return (
    <form onSubmit={onSubmit} noValidate>
      <CardContent className="flex flex-col gap-4 pt-4">
        {/* FR-C05 UI.md 4b / R-J6: tin dang mo nhung chua thoa R-J3 (du lieu cu truoc danh muc). Tinh tu
            du lieu DA LUU, khong tu gia tri dang sua trong form. */}
        {job.status === 'OPEN' && !isCatalogComplete(job) && (
          <div
            className="flex items-start gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container-high p-3 text-m3-body-md text-m3-on-surface"
          >
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-m3-on-surface" aria-hidden="true" />
            <p>
              Tin đang mở nhưng ngành nghề hoặc tỉnh/thành chưa chọn từ danh mục. Tin vẫn hiển thị bình thường; ứng
              viên lọc theo danh mục có thể không thấy tin này. Chọn lại rồi lưu.
            </p>
          </div>
        )}
        <div className="flex items-center gap-2 text-sm text-ink-muted">
          <span>Trạng thái hiện tại:</span>
          <JobStatusBadge status={job.status} />
          <span>· Chu kỳ tuyển dụng: {job.recruitmentCycle}</span>
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="edit-title">Tiêu đề tin tuyển dụng</Label>
          <Input id="edit-title" {...register('title')} />
          {errors.title && <p className="text-sm text-danger">{errors.title.message}</p>}
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="edit-description">Mô tả công việc</Label>
          <Textarea id="edit-description" rows={5} {...register('description')} />
          {errors.description && <p className="text-sm text-danger">{errors.description.message}</p>}
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="edit-requirements">Yêu cầu ứng viên</Label>
          <Textarea id="edit-requirements" rows={4} {...register('requirements')} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Controller
            control={control}
            name="categoryCode"
            render={({ field }) => (
              <CatalogField
                id="edit-category"
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
                legacyValue={job.legacyCategory}
              />
            )}
          />
          <Controller
            control={control}
            name="locationCode"
            render={({ field }) => (
              <CatalogField
                id="edit-location"
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
                legacyValue={job.legacyLocation}
              />
            )}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="edit-employment-type">Loại hình làm việc</Label>
            <Controller
              control={control}
              name="employmentType"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="edit-employment-type" className="w-full">
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
            <Label htmlFor="edit-work-mode">Hình thức làm việc</Label>
            <Controller
              control={control}
              name="workMode"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="edit-work-mode" className="w-full">
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

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="edit-salary-min">Lương tối thiểu</Label>
            <Input id="edit-salary-min" type="number" min={0} {...register('salaryMin')} />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="edit-salary-max">Lương tối đa</Label>
            <Input id="edit-salary-max" type="number" min={0} {...register('salaryMax')} />
            {errors.salaryMax && <p className="text-sm text-danger">{errors.salaryMax.message}</p>}
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="edit-salary-currency">Đơn vị tiền tệ</Label>
            <Input id="edit-salary-currency" placeholder="VND" {...register('salaryCurrency')} />
            {errors.salaryCurrency && <p className="text-sm text-danger">{errors.salaryCurrency.message}</p>}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="edit-deadline">Hạn ứng tuyển</Label>
            <Input id="edit-deadline" type="date" {...register('deadline')} />
          </div>
        </div>

        {updateMutation.isError && (
          <p className="text-sm text-danger">
            {extractErrorMessage(updateMutation.error, 'Lưu thất bại, vui lòng thử lại.')}
          </p>
        )}
      </CardContent>

      <CardFooter className="flex items-center gap-3">
        <Button type="submit" disabled={isSubmitting || updateMutation.isPending}>
          {updateMutation.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}
        </Button>
        {showSuccess && !isDirty && (
          <p className="rounded-(--radius-badge) bg-brand-light px-3 py-2 text-sm text-brand">Đã lưu thay đổi</p>
        )}
      </CardFooter>
    </form>
  )
}

// ---- Tab "Mau giay moi phong van" — GET/PUT /hr/jobs/{jobId}/interview-template, doc lap ----

const templateSchema = z.object({
  subject: z.string().trim().min(1, 'Vui lòng nhập tiêu đề thư mời').max(255, 'Tối đa 255 ký tự'),
  body: z.string().trim().min(1, 'Vui lòng nhập nội dung thư mời'),
  senderName: z.string().trim().min(1, 'Vui lòng nhập tên người gửi').max(150, 'Tối đa 150 ký tự'),
  senderTitle: z.string().max(150, 'Tối đa 150 ký tự').optional(),
  address: z.string().optional(),
})

type TemplateFormValues = z.infer<typeof templateSchema>

function toTemplatePayload(values: TemplateFormValues): InterviewTemplateOwnerRequest {
  return {
    subject: values.subject.trim(),
    body: values.body.trim(),
    senderName: values.senderName.trim(),
    senderTitle: toUndef(values.senderTitle),
    address: toUndef(values.address),
  }
}

function InterviewTemplateTab({ jobId }: { jobId: string }) {
  const { data: template, isLoading, isError } = useInterviewTemplateQuery(jobId)
  const updateMutation = useUpdateInterviewTemplateMutation(jobId)
  const [showSuccess, setShowSuccess] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<TemplateFormValues>({
    resolver: zodResolver(templateSchema),
    values: template
      ? {
          subject: template.subject,
          body: template.body,
          senderName: template.senderName,
          senderTitle: template.senderTitle ?? '',
          address: template.address ?? '',
        }
      : undefined,
  })

  useEffect(() => {
    if (!showSuccess) return
    const timer = setTimeout(() => setShowSuccess(false), SAVE_SUCCESS_TIMEOUT_MS)
    return () => clearTimeout(timer)
  }, [showSuccess])

  const onSubmit = handleSubmit(async (values) => {
    setShowSuccess(false)
    try {
      await updateMutation.mutateAsync(toTemplatePayload(values))
      setShowSuccess(true)
    } catch {
      // updateMutation.isError da phan anh loi nay ra UI ben duoi, khong can lam gi them.
    }
  })

  if (isLoading) {
    return <p className="p-6 text-sm text-ink-muted">Đang tải...</p>
  }
  if (isError || !template) {
    return <p className="p-6 text-sm text-danger">Không tải được mẫu giấy mời, vui lòng thử lại.</p>
  }

  return (
    <form onSubmit={onSubmit} noValidate>
      <CardContent className="flex flex-col gap-4 pt-4">
        <p className="text-sm text-ink-muted">
          Tên công ty (<span className="font-medium text-ink">{template.companyName}</span>) lấy tự động từ hồ sơ
          công ty, không sửa được ở đây. Mẫu này dùng chung cho mọi ứng viên của tin tuyển dụng — ngày giờ phỏng vấn
          cụ thể sẽ do bạn điền khi mời từng ứng viên, không có ở form này.
        </p>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="tpl-edit-subject">Tiêu đề thư mời</Label>
          <Input id="tpl-edit-subject" {...register('subject')} />
          {errors.subject && <p className="text-sm text-danger">{errors.subject.message}</p>}
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="tpl-edit-body">Nội dung thư mời</Label>
          <Textarea id="tpl-edit-body" rows={6} {...register('body')} />
          {errors.body && <p className="text-sm text-danger">{errors.body.message}</p>}
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="tpl-edit-sender-name">Người gửi</Label>
            <Input id="tpl-edit-sender-name" {...register('senderName')} />
            {errors.senderName && <p className="text-sm text-danger">{errors.senderName.message}</p>}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="tpl-edit-sender-title">Chức danh người gửi</Label>
            <Input id="tpl-edit-sender-title" {...register('senderTitle')} />
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="tpl-edit-address">Địa chỉ phỏng vấn</Label>
          <Textarea id="tpl-edit-address" rows={2} {...register('address')} />
        </div>

        {updateMutation.isError && (
          <p className="text-sm text-danger">
            {extractErrorMessage(updateMutation.error, 'Lưu thất bại, vui lòng thử lại.')}
          </p>
        )}
      </CardContent>

      <CardFooter className="flex items-center gap-3">
        <Button type="submit" disabled={isSubmitting || updateMutation.isPending}>
          {updateMutation.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}
        </Button>
        {showSuccess && !isDirty && (
          <p className="rounded-(--radius-badge) bg-brand-light px-3 py-2 text-sm text-brand">Đã lưu thay đổi</p>
        )}
      </CardFooter>
    </form>
  )
}

const VALID_TABS = ['job', 'template', 'rubric', 'applications'] as const

export function HrJobEditPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  if (!id) {
    return null
  }

  const tabParam = searchParams.get('tab')
  const initialTab = (VALID_TABS as readonly string[]).includes(tabParam ?? '') ? (tabParam as string) : 'job'
  const justCreated = searchParams.get('created') === '1'

  return (
    <HrLayout title="Sửa tin tuyển dụng">
      <Card className="mx-auto max-w-5xl">
        <Tabs defaultValue={initialTab}>
          <CardHeader>
            <CardTitle>Sửa tin tuyển dụng</CardTitle>
            {justCreated && (
              <p className="rounded-(--radius-badge) bg-brand-light px-3 py-2 text-sm text-brand">
                Tin đã được tạo ở trạng thái Nháp. Thêm tiêu chí đánh giá đủ 100% trọng số để có thể mở tin tuyển
                dụng.
              </p>
            )}
            <TabsList className="mt-2 w-fit">
              <TabsTrigger value="job">Thông tin tin tuyển dụng</TabsTrigger>
              <TabsTrigger value="template">Mẫu giấy mời phỏng vấn</TabsTrigger>
              <TabsTrigger value="rubric">Rubric chấm điểm</TabsTrigger>
              <TabsTrigger value="applications">Ứng viên</TabsTrigger>
            </TabsList>
          </CardHeader>

          <TabsContent value="job">
            <JobInfoTab jobId={id} />
          </TabsContent>
          <TabsContent value="template">
            <InterviewTemplateTab jobId={id} />
          </TabsContent>
          <TabsContent value="rubric">
            <RubricTab jobId={id} />
            {justCreated && (
              // Chi hien khi vao tu luong vua tao tin (created=1) - day la loi nhac "lam gi tiep"
              // mot lan, gan voi banner phia tren. Khi HR quay lai tab nay o phien lam viec binh
              // thuong (khong co created=1), dieu huong sidebar da du, khong can lap lai 2 nut nay.
              <CardFooter className="flex items-center justify-end gap-3">
                <Button type="button" variant="outline" onClick={() => navigate('/hr/jobs')}>
                  Thiết lập sau
                </Button>
                <Button type="button" onClick={() => navigate('/hr/jobs')}>
                  Xong
                </Button>
              </CardFooter>
            )}
          </TabsContent>
          <TabsContent value="applications">
            <ApplicationsTab jobId={id} />
          </TabsContent>
        </Tabs>
      </Card>
    </HrLayout>
  )
}

import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { Controller, useForm } from 'react-hook-form'
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
import { EMPLOYMENT_TYPE_LABELS, EMPLOYMENT_TYPE_OPTIONS, WORK_MODE_LABELS, WORK_MODE_OPTIONS } from '../features/jobs/jobLabels'
import { useCreateHrJobMutation } from '../features/jobs/ownerQueries'
import type { JobCreateOwnerRequest } from '../features/jobs/ownerTypes'

const createJobSchema = z
  .object({
    title: z.string().trim().min(1, 'Vui lòng nhập tiêu đề').max(200, 'Tối đa 200 ký tự'),
    description: z.string().trim().min(1, 'Vui lòng nhập mô tả công việc'),
    requirements: z.string().optional(),
    category: z.string().max(120, 'Tối đa 120 ký tự').optional(),
    location: z.string().max(150, 'Tối đa 150 ký tự').optional(),
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

type CreateJobFormValues = z.infer<typeof createJobSchema>

const EMPTY_VALUES: CreateJobFormValues = {
  title: '',
  description: '',
  requirements: '',
  category: '',
  location: '',
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

const STEPS: { title: string; fields: (keyof CreateJobFormValues)[] }[] = [
  {
    title: 'Thông tin cơ bản',
    fields: ['title', 'description', 'requirements', 'category', 'location', 'employmentType', 'workMode'],
  },
  { title: 'Lương & thời hạn', fields: ['salaryMin', 'salaryMax', 'salaryCurrency', 'deadline'] },
  { title: 'Mẫu giấy mời phỏng vấn', fields: ['subject', 'body', 'senderName', 'senderTitle', 'address'] },
]

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

function toPayload(values: CreateJobFormValues): JobCreateOwnerRequest {
  return {
    job: {
      title: values.title.trim(),
      description: values.description.trim(),
      requirements: toUndef(values.requirements),
      category: toUndef(values.category),
      location: toUndef(values.location),
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

export function HrJobCreatePage() {
  const navigate = useNavigate()
  const createMutation = useCreateHrJobMutation()
  const [step, setStep] = useState(0)

  const {
    register,
    control,
    trigger,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<CreateJobFormValues>({
    resolver: zodResolver(createJobSchema),
    defaultValues: EMPTY_VALUES,
  })

  async function handleNext() {
    const valid = await trigger(STEPS[step].fields)
    if (valid) {
      setStep((s) => Math.min(s + 1, STEPS.length - 1))
    }
  }

  function handleBack() {
    setStep((s) => Math.max(s - 1, 0))
  }

  const onSubmit = handleSubmit(async (values) => {
    try {
      const created = await createMutation.mutateAsync(toPayload(values))
      navigate(`/hr/jobs/${created.id}/edit`)
    } catch {
      // createMutation.isError da phan anh loi nay ra UI ben duoi, khong can lam gi them.
    }
  })

  const isLastStep = step === STEPS.length - 1

  return (
    <HrLayout title="Tạo tin tuyển dụng">
      <form onSubmit={onSubmit} noValidate>
        <Card className="mx-auto max-w-2xl">
          <CardHeader>
            <CardTitle>Tạo tin tuyển dụng</CardTitle>
            <div className="mt-2 flex items-center gap-2">
              {STEPS.map((s, index) => (
                <div key={s.title} className="flex flex-1 items-center gap-2">
                  <div
                    className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-medium ${
                      index <= step ? 'bg-brand text-white' : 'bg-canvas text-ink-muted'
                    }`}
                  >
                    {index + 1}
                  </div>
                  <span className={`text-xs ${index === step ? 'font-medium text-ink' : 'text-ink-muted'}`}>
                    {s.title}
                  </span>
                  {index < STEPS.length - 1 && <div className="h-px flex-1 bg-line" />}
                </div>
              ))}
            </div>
          </CardHeader>

          <CardContent className="flex flex-col gap-4">
            {step === 0 && (
              <>
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="job-title">Tiêu đề tin tuyển dụng</Label>
                  <Input id="job-title" {...register('title')} />
                  {errors.title && <p className="text-sm text-danger">{errors.title.message}</p>}
                </div>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="job-description">Mô tả công việc</Label>
                  <Textarea id="job-description" rows={5} {...register('description')} />
                  {errors.description && <p className="text-sm text-danger">{errors.description.message}</p>}
                </div>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="job-requirements">Yêu cầu ứng viên</Label>
                  <Textarea id="job-requirements" rows={4} {...register('requirements')} />
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="job-category">Danh mục</Label>
                    <Input id="job-category" {...register('category')} />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="job-location">Địa điểm</Label>
                    <Input id="job-location" {...register('location')} />
                  </div>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="job-employment-type">Loại hình làm việc</Label>
                    <Controller
                      control={control}
                      name="employmentType"
                      render={({ field }) => (
                        <Select value={field.value || undefined} onValueChange={field.onChange}>
                          <SelectTrigger id="job-employment-type" className="w-full">
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
                    <Label htmlFor="job-work-mode">Hình thức làm việc</Label>
                    <Controller
                      control={control}
                      name="workMode"
                      render={({ field }) => (
                        <Select value={field.value || undefined} onValueChange={field.onChange}>
                          <SelectTrigger id="job-work-mode" className="w-full">
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

            {step === 1 && (
              <>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="job-salary-min">Lương tối thiểu</Label>
                    <Input id="job-salary-min" type="number" min={0} {...register('salaryMin')} />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="job-salary-max">Lương tối đa</Label>
                    <Input id="job-salary-max" type="number" min={0} {...register('salaryMax')} />
                    {errors.salaryMax && <p className="text-sm text-danger">{errors.salaryMax.message}</p>}
                  </div>
                </div>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="job-salary-currency">Đơn vị tiền tệ</Label>
                  <Input id="job-salary-currency" placeholder="VND" {...register('salaryCurrency')} />
                  {errors.salaryCurrency && <p className="text-sm text-danger">{errors.salaryCurrency.message}</p>}
                </div>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="job-deadline">Hạn ứng tuyển</Label>
                  <Input id="job-deadline" type="date" {...register('deadline')} />
                </div>
              </>
            )}

            {step === 2 && (
              <>
                <p className="text-sm text-ink-muted">
                  Mẫu giấy mời phỏng vấn dùng chung cho mọi ứng viên của tin này. Ngày giờ phỏng vấn cụ thể sẽ do bạn
                  điền khi mời từng ứng viên sau này, không cấu hình ở đây.
                </p>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="tpl-subject">Tiêu đề thư mời</Label>
                  <Input id="tpl-subject" {...register('subject')} />
                  {errors.subject && <p className="text-sm text-danger">{errors.subject.message}</p>}
                </div>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor="tpl-body">Nội dung thư mời</Label>
                  <Textarea id="tpl-body" rows={6} {...register('body')} />
                  {errors.body && <p className="text-sm text-danger">{errors.body.message}</p>}
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="tpl-sender-name">Người gửi</Label>
                    <Input id="tpl-sender-name" {...register('senderName')} />
                    {errors.senderName && <p className="text-sm text-danger">{errors.senderName.message}</p>}
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
              </>
            )}

            {createMutation.isError && (
              <p className="text-sm text-danger">
                {extractErrorMessage(createMutation.error, 'Tạo tin tuyển dụng thất bại, vui lòng thử lại.')}
              </p>
            )}
          </CardContent>

          <CardFooter className="flex items-center justify-between">
            <Button type="button" variant="outline" onClick={handleBack} disabled={step === 0}>
              Quay lại
            </Button>
            {isLastStep ? (
              <Button type="submit" disabled={isSubmitting || createMutation.isPending}>
                {createMutation.isPending ? 'Đang tạo...' : 'Tạo tin tuyển dụng'}
              </Button>
            ) : (
              <Button type="button" onClick={handleNext}>
                Tiếp theo
              </Button>
            )}
          </CardFooter>
        </Card>
      </form>
    </HrLayout>
  )
}

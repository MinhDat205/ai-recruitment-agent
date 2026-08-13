import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { Plus, Trash2 } from 'lucide-react'
import { useEffect } from 'react'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useAddRubricCriterionMutation, useUpdateRubricCriterionMutation } from './ownerQueries'
import type { RubricCriterionRequest, RubricCriterionResponse } from './ownerTypes'

function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

function formatPercent(value: number): string {
  return Number(value.toFixed(2)).toString()
}

const scaleLevelSchema = z.object({
  level: z
    .string()
    .trim()
    .min(1, 'Nhập mức điểm')
    .refine((v) => Number.isInteger(Number(v)) && Number(v) > 0, 'Mức điểm phải là số nguyên dương'),
  description: z.string().trim().min(1, 'Nhập mô tả cho mức này'),
})

const criterionSchema = z.object({
  name: z.string().trim().min(1, 'Vui lòng nhập tên tiêu chí').max(200, 'Tối đa 200 ký tự'),
  description: z.string().optional(),
  weight: z
    .string()
    .trim()
    .min(1, 'Vui lòng nhập trọng số')
    .refine((v) => Number(v) > 0 && Number(v) <= 100, 'Trọng số phải trong khoảng lớn hơn 0 và tối đa 100'),
  maxScore: z
    .string()
    .optional()
    .refine((v) => !v || (Number.isInteger(Number(v)) && Number(v) > 0), 'Thang điểm phải là số nguyên dương'),
  scaleLevels: z.array(scaleLevelSchema),
})

type CriterionFormValues = z.infer<typeof criterionSchema>

interface CriterionFormDialogProps {
  jobId: string
  open: boolean
  onOpenChange: (open: boolean) => void
  // Tong trong so cua CAC tieu chi khac (khong tinh chinh tieu chi dang sua, neu la sua) - dung
  // de tinh tong du kien sau khi luu, hien thi realtime khi HR go trong so.
  otherCriteriaWeight: number
  nextDisplayOrder: number
  criterion?: RubricCriterionResponse
}

export function CriterionFormDialog({
  jobId,
  open,
  onOpenChange,
  otherCriteriaWeight,
  nextDisplayOrder,
  criterion,
}: CriterionFormDialogProps) {
  const isEdit = Boolean(criterion)
  const addMutation = useAddRubricCriterionMutation(jobId)
  const updateMutation = useUpdateRubricCriterionMutation(jobId)
  const mutation = isEdit ? updateMutation : addMutation

  const {
    register,
    control,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CriterionFormValues>({
    resolver: zodResolver(criterionSchema),
    defaultValues: { name: '', description: '', weight: '', maxScore: '', scaleLevels: [] },
  })

  const { fields, append, remove } = useFieldArray({ control, name: 'scaleLevels' })
  const weightValue = useWatch({ control, name: 'weight' })

  useEffect(() => {
    if (!open) return
    if (criterion) {
      reset({
        name: criterion.name,
        description: criterion.description ?? '',
        weight: String(criterion.weight),
        maxScore: String(criterion.maxScore),
        scaleLevels: (criterion.scaleDescription ?? []).map((l) => ({
          level: String(l.level),
          description: l.description,
        })),
      })
    } else {
      reset({ name: '', description: '', weight: '', maxScore: '', scaleLevels: [] })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, criterion])

  // Backend chi chan khi tong SE VUOT 100% (400 RUBRIC_WEIGHT_EXCEEDED) - duoi 100% van luu duoc
  // binh thuong vi HR co the dang xay rubric tung buoc (tieu chi 1/3 chua the =100%). UI chi hien
  // thi truoc, khong phai co che chan rieng cua frontend.
  const projectedTotal = otherCriteriaWeight + (Number(weightValue) || 0)
  const exceeds = projectedTotal > 100 + 1e-9

  const onSubmit = handleSubmit(async (values) => {
    const payload: RubricCriterionRequest = {
      name: values.name.trim(),
      description: values.description?.trim() ? values.description.trim() : undefined,
      scaleDescription:
        values.scaleLevels.length > 0
          ? values.scaleLevels.map((l) => ({ level: Number(l.level), description: l.description.trim() }))
          : undefined,
      weight: Number(values.weight),
      maxScore: values.maxScore ? Number(values.maxScore) : undefined,
      displayOrder: criterion ? criterion.displayOrder : nextDisplayOrder,
    }

    if (criterion) {
      await updateMutation.mutateAsync({ criterionId: criterion.id, payload })
    } else {
      await addMutation.mutateAsync(payload)
    }
    onOpenChange(false)
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? 'Sửa tiêu chí' : 'Thêm tiêu chí'}</DialogTitle>
          <DialogDescription>Mỗi tiêu chí được AI chấm điểm riêng lẻ theo trọng số bạn đặt ở đây.</DialogDescription>
        </DialogHeader>

        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="criterion-name">Tên tiêu chí</Label>
            <Input id="criterion-name" {...register('name')} />
            {errors.name && <p className="text-sm text-danger">{errors.name.message}</p>}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="criterion-description">Mô tả</Label>
            <Textarea id="criterion-description" rows={2} {...register('description')} />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="criterion-weight">Trọng số (%)</Label>
              <Input id="criterion-weight" type="number" step="0.01" min={0.01} max={100} {...register('weight')} />
              {errors.weight && <p className="text-sm text-danger">{errors.weight.message}</p>}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="criterion-max-score">Thang điểm tối đa</Label>
              <Input
                id="criterion-max-score"
                type="number"
                min={1}
                placeholder="5 (mặc định)"
                {...register('maxScore')}
              />
              {errors.maxScore && <p className="text-sm text-danger">{errors.maxScore.message}</p>}
            </div>
          </div>

          <p className={`text-sm ${exceeds ? 'text-danger' : 'text-ink-muted'}`}>
            Tổng trọng số của cả rubric sau khi lưu: {formatPercent(projectedTotal)}%
            {exceeds && ' — vượt quá 100%, không lưu được.'}
          </p>

          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <Label>Mô tả thang điểm theo từng mức</Label>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => append({ level: '', description: '' })}
              >
                <Plus /> Thêm mức
              </Button>
            </div>
            {/* Khong bat buoc - khong danh dau sao do, khong canh bao. Bo trong la trang thai binh
                thuong, khong phai thieu sot. */}
            <p className="text-sm text-ink-muted">
              Không bắt buộc. Nếu để trống, AI sẽ chấm theo thang điểm mặc định dùng chung cho mọi tiêu chí.
            </p>

            {fields.map((field, index) => (
              <div key={field.id} className="flex items-start gap-2">
                <Input
                  type="number"
                  min={1}
                  placeholder="Mức"
                  className="w-20"
                  {...register(`scaleLevels.${index}.level`)}
                />
                <div className="flex-1">
                  <Input placeholder="Mô tả cho mức điểm này" {...register(`scaleLevels.${index}.description`)} />
                  {errors.scaleLevels?.[index]?.level && (
                    <p className="text-sm text-danger">{errors.scaleLevels[index]?.level?.message}</p>
                  )}
                  {errors.scaleLevels?.[index]?.description && (
                    <p className="text-sm text-danger">{errors.scaleLevels[index]?.description?.message}</p>
                  )}
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  aria-label="Xoá mức này"
                  onClick={() => remove(index)}
                >
                  <Trash2 />
                </Button>
              </div>
            ))}
          </div>

          {mutation.isError && (
            <p className="text-sm text-danger">
              {extractErrorMessage(mutation.error, 'Lưu thất bại, vui lòng thử lại.')}
            </p>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Huỷ
            </Button>
            <Button type="submit" disabled={isSubmitting || mutation.isPending || exceeds}>
              {mutation.isPending ? 'Đang lưu...' : 'Lưu'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

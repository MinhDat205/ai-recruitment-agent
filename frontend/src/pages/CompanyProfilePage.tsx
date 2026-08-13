import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { useEffect, useState, type ChangeEvent } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { HrLayout } from '../components/layout/HrLayout'
import { useMyCompanyQuery, useSaveCompanyMutation, useUploadLogoMutation } from '../features/companies/ownerQueries'
import type { CompanyOwnerRequest } from '../features/companies/ownerTypes'

const companySchema = z.object({
  name: z.string().min(1, 'Vui lòng nhập tên công ty'),
  description: z.string().optional(),
  companySize: z.string().optional(),
  industry: z.string().optional(),
  website: z.string().optional(),
  contactEmail: z.string().email('Email không hợp lệ').optional().or(z.literal('')),
  contactPhone: z.string().optional(),
  address: z.string().optional(),
})

type CompanyFormValues = z.infer<typeof companySchema>

const SAVE_SUCCESS_TIMEOUT_MS = 4000

// Backend tra loi qua ErrorResponse { error, message } (xem GlobalExceptionHandler) - lay dung
// message do thay vi chuoi cung, chi fallback khi response khong dung dang nay (vd loi mang).
function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

const EMPTY_VALUES: CompanyFormValues = {
  name: '',
  description: '',
  companySize: '',
  industry: '',
  website: '',
  contactEmail: '',
  contactPhone: '',
  address: '',
}

function toPayload(values: CompanyFormValues): CompanyOwnerRequest {
  const trimmed = (value?: string) => (value && value.trim() !== '' ? value.trim() : undefined)
  return {
    name: values.name.trim(),
    description: trimmed(values.description),
    companySize: trimmed(values.companySize),
    industry: trimmed(values.industry),
    website: trimmed(values.website),
    contactEmail: trimmed(values.contactEmail),
    contactPhone: trimmed(values.contactPhone),
    address: trimmed(values.address),
  }
}

export function CompanyProfilePage() {
  const { data: company, isLoading, isError, error } = useMyCompanyQuery()
  const notFound = isError && isAxiosError(error) && error.response?.status === 404
  const otherError = isError && !notFound

  const saveMutation = useSaveCompanyMutation(company?.id)
  const uploadLogoMutation = useUploadLogoMutation(company?.id)
  const [logoError, setLogoError] = useState<string | null>(null)
  const [saveKind, setSaveKind] = useState<'create' | 'update' | null>(null)
  const [showSaveSuccess, setShowSaveSuccess] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<CompanyFormValues>({
    resolver: zodResolver(companySchema),
    values: company
      ? {
          name: company.name,
          description: company.description ?? '',
          companySize: company.companySize ?? '',
          industry: company.industry ?? '',
          website: company.website ?? '',
          contactEmail: company.contactEmail ?? '',
          contactPhone: company.contactPhone ?? '',
          address: company.address ?? '',
        }
      : EMPTY_VALUES,
  })

  // Tu an banner sau vai giay. isDirty tu tro lai false ngay khi values (sync tu query cache)
  // cap nhat sau luc luu thanh cong, nen dieu kien hien thi ben duoi (showSaveSuccess && !isDirty)
  // tu dong an khi nguoi dung go tiep vao bat ky field nao - khong can theo doi tung field rieng.
  useEffect(() => {
    if (!showSaveSuccess) {
      return
    }
    const timer = setTimeout(() => setShowSaveSuccess(false), SAVE_SUCCESS_TIMEOUT_MS)
    return () => clearTimeout(timer)
  }, [showSaveSuccess])

  const onSubmit = handleSubmit(async (values) => {
    setShowSaveSuccess(false)
    const kind = company ? 'update' : 'create'
    try {
      await saveMutation.mutateAsync(toPayload(values))
      setSaveKind(kind)
      setShowSaveSuccess(true)
    } catch {
      // saveMutation.isError da phan anh loi nay ra UI ben duoi, khong can lam gi them.
    }
  })

  function handleLogoChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) {
      return
    }
    setLogoError(null)
    uploadLogoMutation.mutate(file, {
      onError: (err) => {
        setLogoError(extractErrorMessage(err, 'Tải logo thất bại, vui lòng thử lại.'))
      },
    })
  }

  const saveSuccessVisible = showSaveSuccess && !isDirty

  if (isLoading) {
    return (
      <HrLayout title="Hồ sơ công ty">
        <p className="text-sm text-muted-foreground">Đang tải...</p>
      </HrLayout>
    )
  }

  if (otherError) {
    return (
      <HrLayout title="Hồ sơ công ty">
        <p className="text-sm text-destructive">Không tải được thông tin công ty, vui lòng thử lại.</p>
      </HrLayout>
    )
  }

  const logoDisabled = !company || uploadLogoMutation.isPending

  return (
    <HrLayout title="Hồ sơ công ty">
      <form onSubmit={onSubmit} noValidate>
        <Card className="mx-auto max-w-2xl">
          <CardHeader>
            <CardTitle>Hồ sơ công ty</CardTitle>
          </CardHeader>

          <CardContent className="flex flex-col gap-6">
            <div className="flex items-center gap-4">
              <div className="h-20 w-20 shrink-0 overflow-hidden rounded-lg border border-border bg-muted">
                {company?.logoUrl && (
                  <img src={company.logoUrl} alt={company.name} className="h-full w-full object-cover" />
                )}
              </div>
              <div className="flex flex-col gap-1">
                <Button
                  asChild
                  type="button"
                  variant="outline"
                  className={logoDisabled ? 'pointer-events-none opacity-50' : undefined}
                >
                  <label htmlFor="company-logo">
                    {uploadLogoMutation.isPending ? 'Đang tải lên...' : 'Đổi logo'}
                  </label>
                </Button>
                <input
                  id="company-logo"
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  className="hidden"
                  disabled={logoDisabled}
                  onChange={handleLogoChange}
                />
                {!company && (
                  <p className="text-xs text-muted-foreground">Tạo hồ sơ công ty trước khi tải logo</p>
                )}
                {logoError && <p className="text-xs text-destructive">{logoError}</p>}
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="company-name">Tên công ty</Label>
              <Input id="company-name" {...register('name')} />
              {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="company-description">Mô tả</Label>
              <Textarea id="company-description" rows={4} {...register('description')} />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="company-size">Quy mô</Label>
                <Input id="company-size" placeholder="vd. 50-100 nhân viên" {...register('companySize')} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="company-industry">Lĩnh vực</Label>
                <Input id="company-industry" {...register('industry')} />
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="company-website">Website</Label>
              <Input id="company-website" {...register('website')} />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="company-email">Email liên hệ</Label>
                <Input id="company-email" type="email" {...register('contactEmail')} />
                {errors.contactEmail && (
                  <p className="text-sm text-destructive">{errors.contactEmail.message}</p>
                )}
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="company-phone">Số điện thoại</Label>
                <Input id="company-phone" type="tel" {...register('contactPhone')} />
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="company-address">Địa chỉ</Label>
              <Textarea id="company-address" rows={2} {...register('address')} />
            </div>

            {saveMutation.isError && (
              <p className="text-sm text-danger">
                {extractErrorMessage(saveMutation.error, 'Lưu thất bại, vui lòng thử lại.')}
              </p>
            )}
          </CardContent>

          <CardFooter className="flex items-center gap-3">
            <Button type="submit" disabled={isSubmitting || saveMutation.isPending}>
              {saveMutation.isPending ? 'Đang lưu...' : notFound ? 'Tạo hồ sơ công ty' : 'Lưu thay đổi'}
            </Button>
            {saveSuccessVisible && (
              <p className="rounded-(--radius-badge) bg-brand-light px-3 py-2 text-sm text-brand">
                {saveKind === 'create' ? 'Đã tạo hồ sơ công ty' : 'Đã lưu thay đổi'}
              </p>
            )}
          </CardFooter>
        </Card>
      </form>
    </HrLayout>
  )
}

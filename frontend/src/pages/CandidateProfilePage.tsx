import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PublicLayout } from '../components/layout/PublicLayout'
import { useMyProfileQuery, useSaveProfileMutation } from '../features/candidateProfile/queries'
import type { CandidateProfileRequest } from '../features/candidateProfile/types'
import { ResumeList } from '../features/resumes/ResumeList'
import { ResumeUploadDropzone } from '../features/resumes/ResumeUploadDropzone'

const profileSchema = z.object({
  headline: z.string().optional(),
  location: z.string().optional(),
  currentTitle: z.string().optional(),
  yearsExperience: z.string().optional(),
  dateOfBirth: z.string().optional(),
})

type ProfileFormValues = z.infer<typeof profileSchema>

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

function toPayload(values: ProfileFormValues): CandidateProfileRequest {
  const trimmed = (value?: string) => (value && value.trim() !== '' ? value.trim() : undefined)
  const years = trimmed(values.yearsExperience)
  return {
    headline: trimmed(values.headline),
    location: trimmed(values.location),
    currentTitle: trimmed(values.currentTitle),
    yearsExperience: years ? Number(years) : undefined,
    dateOfBirth: trimmed(values.dateOfBirth),
  }
}

export function CandidateProfilePage() {
  const { data: profile, isLoading, isError } = useMyProfileQuery()
  const saveMutation = useSaveProfileMutation()
  const [showSaveSuccess, setShowSaveSuccess] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { isSubmitting, isDirty },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    values: profile
      ? {
          headline: profile.headline ?? '',
          location: profile.location ?? '',
          currentTitle: profile.currentTitle ?? '',
          yearsExperience: profile.yearsExperience?.toString() ?? '',
          dateOfBirth: profile.dateOfBirth ?? '',
        }
      : undefined,
  })

  // Tu an banner sau vai giay, tuong tu CompanyProfilePage.
  useEffect(() => {
    if (!showSaveSuccess) {
      return
    }
    const timer = setTimeout(() => setShowSaveSuccess(false), SAVE_SUCCESS_TIMEOUT_MS)
    return () => clearTimeout(timer)
  }, [showSaveSuccess])

  const onSubmit = handleSubmit(async (values) => {
    setShowSaveSuccess(false)
    try {
      await saveMutation.mutateAsync(toPayload(values))
      setShowSaveSuccess(true)
    } catch {
      // saveMutation.isError da phan anh loi nay ra UI ben duoi, khong can lam gi them.
    }
  })

  const saveSuccessVisible = showSaveSuccess && !isDirty

  return (
    <PublicLayout>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-4 py-8 md:px-6">
        <form onSubmit={onSubmit} noValidate>
          <Card>
            <CardHeader>
              <CardTitle>Thông tin cá nhân</CardTitle>
            </CardHeader>

            <CardContent className="flex flex-col gap-6">
              {isLoading && <p className="text-sm text-ink-muted">Đang tải...</p>}
              {isError && <p className="text-sm text-danger">Không tải được hồ sơ, vui lòng thử lại.</p>}

              {!isLoading && !isError && (
                <>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="profile-headline">Chức danh mong muốn</Label>
                    <Input id="profile-headline" placeholder="vd. Backend Developer" {...register('headline')} />
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="profile-current-title">Vị trí hiện tại</Label>
                      <Input id="profile-current-title" {...register('currentTitle')} />
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="profile-location">Khu vực</Label>
                      <Input id="profile-location" placeholder="vd. Hồ Chí Minh" {...register('location')} />
                    </div>
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="profile-years">Số năm kinh nghiệm</Label>
                      <Input
                        id="profile-years"
                        type="number"
                        step="0.5"
                        min="0"
                        {...register('yearsExperience')}
                      />
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor="profile-dob">Ngày sinh</Label>
                      <Input id="profile-dob" type="date" {...register('dateOfBirth')} />
                    </div>
                  </div>
                </>
              )}

              {saveMutation.isError && (
                <p className="text-sm text-danger">
                  {extractErrorMessage(saveMutation.error, 'Lưu thất bại, vui lòng thử lại.')}
                </p>
              )}
            </CardContent>

            <CardFooter className="flex items-center gap-3">
              <Button type="submit" disabled={isLoading || isError || isSubmitting || saveMutation.isPending}>
                {saveMutation.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}
              </Button>
              {saveSuccessVisible && (
                <p className="rounded-(--radius-badge) bg-brand-light px-3 py-2 text-sm text-brand">Đã lưu thay đổi</p>
              )}
            </CardFooter>
          </Card>
        </form>

        <Card>
          <CardHeader>
            <CardTitle>CV của tôi</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-6">
            <ResumeUploadDropzone />
            <ResumeList />
          </CardContent>
        </Card>
      </div>
    </PublicLayout>
  )
}

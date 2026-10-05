import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { AlertCircle } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { useCatalogsQuery } from '../features/catalog/queries'
import {
  CareerPreferencesFields,
  MAX_SKILLS,
  type CareerPreferencesValues,
} from '../features/candidateProfile/CareerPreferencesFields'
import { useAutofillFromResumeMutation, useMyProfileQuery, useSaveProfileMutation } from '../features/candidateProfile/queries'
import type { CandidateProfileRequest } from '../features/candidateProfile/types'
import { useResumesQuery } from '../features/resumes/queries'
import { ResumeList } from '../features/resumes/ResumeList'
import { ResumeUploadDropzone } from '../features/resumes/ResumeUploadDropzone'

const profileSchema = z.object({
  // Card "Thong tin co ban" (R-G3 - giu nguyen 3 field, khong them validate)
  location: z.string().optional(),
  currentTitle: z.string().optional(),
  dateOfBirth: z.string().optional(),
  // Card "Nghe nghiep va mong muon cong viec" (FR-U14) - headline/yearsExperience chuyen tu card tren
  // sang day theo UI.md muc 4d ("dung nhom field 4a").
  headline: z.string().optional(),
  desiredIndustryCodes: z.array(z.string()),
  desiredLocationCodes: z.array(z.string()),
  desiredWorkModes: z.array(z.string()),
  desiredSalaryMinMillions: z.string().optional(),
  yearsExperience: z.string().optional(),
  skills: z.array(z.string()),
  bio: z.string().optional(),
})

type ProfileFormValues = z.infer<typeof profileSchema>

const DEFAULT_VALUES: ProfileFormValues = {
  location: '',
  currentTitle: '',
  dateOfBirth: '',
  headline: '',
  desiredIndustryCodes: [],
  desiredLocationCodes: [],
  desiredWorkModes: [],
  desiredSalaryMinMillions: '',
  yearsExperience: '',
  skills: [],
  bio: '',
}

const SAVE_SUCCESS_TIMEOUT_MS = 4000

// Hai field duy nhat co the nhan loi Bean Validation tu backend (desiredSalaryMinMillions @Min/@Max,
// bio @Size) - GlobalExceptionHandler.handleValidation tra ve Map<String,String> thuan (khong boc
// {error,message}), khac het cac exception nghiep vu khac (INVALID_PROFILE_FIELD/INVALID_CATALOG_CODE).
const BEAN_VALIDATION_FIELDS = ['desiredSalaryMinMillions', 'bio'] as const
type BeanValidationField = (typeof BEAN_VALIDATION_FIELDS)[number]

function isBeanValidationField(field: string): field is BeanValidationField {
  return (BEAN_VALIDATION_FIELDS as readonly string[]).includes(field)
}

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
  const salary = trimmed(values.desiredSalaryMinMillions)
  return {
    headline: trimmed(values.headline),
    location: trimmed(values.location),
    currentTitle: trimmed(values.currentTitle),
    yearsExperience: years ? Number(years) : undefined,
    dateOfBirth: trimmed(values.dateOfBirth),
    desiredIndustryCodes: values.desiredIndustryCodes,
    desiredLocationCodes: values.desiredLocationCodes,
    desiredWorkModes: values.desiredWorkModes,
    skills: values.skills,
    desiredSalaryMinMillions: salary ? Number(salary) : undefined,
    bio: trimmed(values.bio),
  }
}

export function CandidateProfilePage() {
  const { data: profile, isLoading, isError } = useMyProfileQuery()
  const catalogsQuery = useCatalogsQuery()
  const { data: resumes } = useResumesQuery()
  const saveMutation = useSaveProfileMutation()
  const autofillMutation = useAutofillFromResumeMutation()
  const [showSaveSuccess, setShowSaveSuccess] = useState(false)
  const [generalError, setGeneralError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    control,
    setValue,
    getValues,
    setError,
    clearErrors,
    formState: { isSubmitting, isDirty, errors },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: DEFAULT_VALUES,
    values: profile
      ? {
          location: profile.location ?? '',
          currentTitle: profile.currentTitle ?? '',
          dateOfBirth: profile.dateOfBirth ?? '',
          headline: profile.headline ?? '',
          desiredIndustryCodes: profile.desiredIndustries.map((item) => item.code),
          desiredLocationCodes: profile.desiredLocations.map((item) => item.code),
          desiredWorkModes: profile.desiredWorkModes,
          desiredSalaryMinMillions: profile.desiredSalaryMinMillions?.toString() ?? '',
          yearsExperience: profile.yearsExperience?.toString() ?? '',
          skills: profile.skills,
          bio: profile.bio ?? '',
        }
      : undefined,
  })

  // Chi "theo doi" (re-render khi doi) dung 8 field can hien thi truc tiep o form (bo dem ky tu, so
  // chip da chon...) - dung useWatch (hook, compiler-safe) thay vi goi watch() ca form (khong
  // memoize duoc, gay warning React Compiler). Noi chi can doc tuc thoi luc bam nut (vd handleAutofill
  // ben duoi) thi dung getValues(), khong can subscribe.
  const [
    headline,
    desiredIndustryCodes,
    desiredLocationCodes,
    desiredWorkModes,
    desiredSalaryMinMillions,
    yearsExperience,
    skills,
    bio,
  ] = useWatch({
    control,
    name: [
      'headline',
      'desiredIndustryCodes',
      'desiredLocationCodes',
      'desiredWorkModes',
      'desiredSalaryMinMillions',
      'yearsExperience',
      'skills',
      'bio',
    ],
  })
  const hasPrimaryResumeDone = (resumes ?? []).some((resume) => resume.isPrimary && resume.parseStatus === 'DONE')

  // Tu an banner sau vai giay, tuong tu CompanyProfilePage.
  useEffect(() => {
    if (!showSaveSuccess) {
      return
    }
    const timer = setTimeout(() => setShowSaveSuccess(false), SAVE_SUCCESS_TIMEOUT_MS)
    return () => clearTimeout(timer)
  }, [showSaveSuccess])

  function handleCareerFieldChange<K extends keyof CareerPreferencesValues>(
    field: K,
    value: CareerPreferencesValues[K],
  ) {
    switch (field) {
      case 'headline':
        setValue('headline', value as string, { shouldDirty: true })
        break
      case 'desiredIndustryCodes':
        setValue('desiredIndustryCodes', value as string[], { shouldDirty: true })
        break
      case 'desiredLocationCodes':
        setValue('desiredLocationCodes', value as string[], { shouldDirty: true })
        break
      case 'desiredWorkModes':
        setValue('desiredWorkModes', value as string[], { shouldDirty: true })
        break
      case 'desiredSalaryMinMillions':
        setValue('desiredSalaryMinMillions', value as string, { shouldDirty: true })
        clearErrors('desiredSalaryMinMillions')
        break
      case 'yearsExperience':
        setValue('yearsExperience', value as string, { shouldDirty: true })
        break
      case 'skills':
        setValue('skills', value as string[], { shouldDirty: true })
        break
      case 'bio':
        setValue('bio', value as string, { shouldDirty: true })
        clearErrors('bio')
        break
    }
  }

  // FR-U14 R-A2 - dien vao FORM, chua luu. currentTitle (CV) luon ghi de "Vi tri hien tai"; chi dien
  // "Chuc danh mong muon" (headline) khi dang trong. Ky nang gop them (dedupe khong phan biet hoa/
  // thuong, ap gioi han 20). So nam kinh nghiem ghi de neu backend co tra.
  async function handleAutofill() {
    try {
      const result = await autofillMutation.mutateAsync()
      if (result.currentTitle) {
        setValue('currentTitle', result.currentTitle, { shouldDirty: true })
        if (!getValues('headline')?.trim()) {
          setValue('headline', result.currentTitle, { shouldDirty: true })
        }
      }
      if (result.skills.length > 0) {
        const current = getValues('skills')
        const lowerSeen = new Set(current.map((skill) => skill.toLowerCase()))
        const merged = [...current]
        for (const skill of result.skills) {
          const trimmedSkill = skill.trim()
          if (trimmedSkill.length === 0) {
            continue
          }
          const key = trimmedSkill.toLowerCase()
          if (lowerSeen.has(key)) {
            continue
          }
          if (merged.length >= MAX_SKILLS) {
            break
          }
          merged.push(trimmedSkill)
          lowerSeen.add(key)
        }
        setValue('skills', merged, { shouldDirty: true })
      }
      if (result.yearsExperience != null) {
        setValue('yearsExperience', result.yearsExperience.toString(), { shouldDirty: true })
      }
    } catch {
      // autofillMutation.isError da phan anh loi nay ra UI canh nut, khong can lam gi them.
    }
  }

  const onSubmit = handleSubmit(async (values) => {
    setShowSaveSuccess(false)
    setGeneralError(null)
    clearErrors(['desiredSalaryMinMillions', 'bio'])
    try {
      await saveMutation.mutateAsync(toPayload(values))
      setShowSaveSuccess(true)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 400) {
        const data = err.response.data as Record<string, unknown>
        if (data && typeof data === 'object' && !('error' in data)) {
          // Loi Bean Validation: Map<String,String> field -> thong diep, hien duoi dung field.
          for (const [field, message] of Object.entries(data)) {
            if (typeof message === 'string' && isBeanValidationField(field)) {
              setError(field, { type: 'server', message })
            }
          }
          return
        }
      }
      // INVALID_PROFILE_FIELD / INVALID_CATALOG_CODE / loi khac: hien 1 dong chung cuoi form.
      setGeneralError(extractErrorMessage(err, 'Lưu thất bại, vui lòng thử lại.'))
    }
  })

  const saveSuccessVisible = showSaveSuccess && !isDirty
  const showFields = !isLoading && !isError
  const cardClassName = 'border border-m3-outline-variant bg-m3-surface text-m3-on-surface ring-0'

  const careerValues: CareerPreferencesValues = {
    headline: headline ?? '',
    desiredIndustryCodes: desiredIndustryCodes ?? [],
    desiredLocationCodes: desiredLocationCodes ?? [],
    desiredWorkModes: desiredWorkModes ?? [],
    desiredSalaryMinMillions: desiredSalaryMinMillions ?? '',
    yearsExperience: yearsExperience ?? '',
    skills: skills ?? [],
    bio: bio ?? '',
  }

  return (
    <CandidateLayout>
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-4 py-8 md:px-6">
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6">
          <Card className={cardClassName}>
            <CardHeader>
              <CardTitle className="text-m3-title-md">Thông tin cơ bản</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-6">
              {isLoading && <p className="text-m3-body-md text-m3-on-surface-variant">Đang tải...</p>}
              {isError && <p className="text-m3-body-md text-m3-error">Không tải được hồ sơ, vui lòng thử lại.</p>}

              {showFields && (
                <div className="grid gap-4 sm:grid-cols-3">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="profile-current-title" className="text-m3-on-surface">
                      Vị trí hiện tại
                    </Label>
                    <input
                      id="profile-current-title"
                      {...register('currentTitle')}
                      className="h-12 w-full rounded-m3-xs border border-m3-outline bg-m3-surface px-3 text-m3-body-md text-m3-on-surface outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary sm:h-10"
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="profile-location" className="text-m3-on-surface">
                      Nơi ở hiện tại
                    </Label>
                    <input
                      id="profile-location"
                      placeholder="vd. Hồ Chí Minh"
                      {...register('location')}
                      className="h-12 w-full rounded-m3-xs border border-m3-outline bg-m3-surface px-3 text-m3-body-md text-m3-on-surface outline-none placeholder:text-m3-on-surface-variant focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary sm:h-10"
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor="profile-dob" className="text-m3-on-surface">
                      Ngày sinh
                    </Label>
                    <input
                      id="profile-dob"
                      type="date"
                      {...register('dateOfBirth')}
                      className="h-12 w-full rounded-m3-xs border border-m3-outline bg-m3-surface px-3 text-m3-body-md text-m3-on-surface outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-m3-primary sm:h-10"
                    />
                  </div>
                </div>
              )}
            </CardContent>
          </Card>

          {showFields && (
            <Card className={cardClassName}>
              <CardHeader>
                <CardTitle className="text-m3-title-md">Nghề nghiệp và mong muốn công việc</CardTitle>
              </CardHeader>
              <CardContent>
                <CareerPreferencesFields
                  idPrefix="profile"
                  values={careerValues}
                  onFieldChange={handleCareerFieldChange}
                  errors={{
                    desiredSalaryMinMillions: errors.desiredSalaryMinMillions?.message,
                    bio: errors.bio?.message,
                  }}
                  catalogs={catalogsQuery.data}
                  catalogsLoading={catalogsQuery.isLoading}
                  catalogsError={catalogsQuery.isError}
                  onRetryCatalogs={() => catalogsQuery.refetch()}
                  onAutofill={handleAutofill}
                  autofillDisabled={!hasPrimaryResumeDone}
                  autofillDisabledReason="Chưa có CV chính đã phân tích xong để điền tự động."
                  autofillPending={autofillMutation.isPending}
                  autofillErrorMessage={
                    autofillMutation.isError ? 'Không điền được, vui lòng thử lại.' : undefined
                  }
                />
              </CardContent>
            </Card>
          )}

          {showFields && (
            <div className="flex flex-wrap items-center gap-3">
              <button
                type="submit"
                disabled={isSubmitting || saveMutation.isPending}
                className="flex h-10 items-center justify-center rounded-m3-button bg-m3-primary px-4 text-m3-label-lg text-m3-on-primary disabled:cursor-not-allowed disabled:opacity-38"
              >
                {saveMutation.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}
              </button>
              {/* Hang nut nam ngoai Card, tren nen trang xam: m3-primary/m3-error chi dat 4.07/4.16:1. */}
              {saveSuccessVisible && <p className="text-m3-body-md text-m3-on-primary-container">Đã lưu thay đổi</p>}
              {generalError && (
                <div role="alert" className="flex items-start gap-2 text-m3-body-md text-m3-on-surface">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
                  <p>{generalError}</p>
                </div>
              )}
            </div>
          )}
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
    </CandidateLayout>
  )
}

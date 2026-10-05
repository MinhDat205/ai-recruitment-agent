import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { CandidateLayout } from '../components/layout/CandidateLayout'
import { useCatalogsQuery } from '../features/catalog/queries'
import {
  CareerPreferencesFields,
  MAX_SKILLS,
  type CareerPreferencesValues,
} from '../features/candidateProfile/CareerPreferencesFields'
import {
  useAutofillFromResumeMutation,
  useMyProfileQuery,
  useSaveProfileMutation,
  useSkipOnboardingMutation,
} from '../features/candidateProfile/queries'
import type { CandidateProfileRequest } from '../features/candidateProfile/types'
import { useResumesQuery } from '../features/resumes/queries'

const onboardingSchema = z.object({
  headline: z.string().optional(),
  desiredIndustryCodes: z.array(z.string()),
  desiredLocationCodes: z.array(z.string()),
  desiredWorkModes: z.array(z.string()),
  desiredSalaryMinMillions: z.string().optional(),
  yearsExperience: z.string().optional(),
  skills: z.array(z.string()),
  bio: z.string().optional(),
})

type OnboardingFormValues = z.infer<typeof onboardingSchema>

const DEFAULT_VALUES: OnboardingFormValues = {
  headline: '',
  desiredIndustryCodes: [],
  desiredLocationCodes: [],
  desiredWorkModes: [],
  desiredSalaryMinMillions: '',
  yearsExperience: '',
  skills: [],
  bio: '',
}

// Hai field duy nhat co the nhan loi Bean Validation tu backend (desiredSalaryMinMillions @Min/@Max,
// bio @Size) - giong het CandidateProfilePage, xem comment o do.
const BEAN_VALIDATION_FIELDS = ['desiredSalaryMinMillions', 'bio'] as const
type BeanValidationField = (typeof BEAN_VALIDATION_FIELDS)[number]

function isBeanValidationField(field: string): field is BeanValidationField {
  return (BEAN_VALIDATION_FIELDS as readonly string[]).includes(field)
}

function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

// Man "Hoan thien ho so" (FR-U14 UI.md muc 4a/4b, REQUIREMENT muc 9 dot 6). CHI gom field nghe
// nghiep/mong muon (CareerPreferencesFields) - KHONG co Vi tri hien tai/Noi o hien tai/Ngay sinh
// (khong trong danh sach field onboarding o REQUIREMENT muc 2). PUT van phai gui du ca 3 field co
// ban (R-V3) de khong vo tinh xoa - lay nguyen gia tri da nap tu ho so, khong cho sua o man nay.
export function CandidateOnboardingPage() {
  const navigate = useNavigate()
  const { data: profile, isLoading, isError } = useMyProfileQuery()
  const catalogsQuery = useCatalogsQuery()
  const { data: resumes } = useResumesQuery()
  const saveMutation = useSaveProfileMutation()
  const skipMutation = useSkipOnboardingMutation()
  const autofillMutation = useAutofillFromResumeMutation()

  const {
    handleSubmit,
    control,
    setValue,
    getValues,
    setError,
    clearErrors,
    formState: { isSubmitting, errors },
  } = useForm<OnboardingFormValues>({
    resolver: zodResolver(onboardingSchema),
    defaultValues: DEFAULT_VALUES,
    values: profile
      ? {
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

  // "Dien tu CV" (R-A2) co the doi currentTitle du man nay khong co o nhap rieng cho no - gia tri
  // duoc giu tam o state nay, gop vao payload luc "Luu" (toPayload ben duoi), KHONG tu luu ngay va
  // khong hien thi o o nhap nao tren man hinh.
  const [currentTitleOverride, setCurrentTitleOverride] = useState<string | undefined>(undefined)
  const [generalError, setGeneralError] = useState<string | null>(null)
  const [skipError, setSkipError] = useState<string | null>(null)

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

  async function handleAutofill() {
    try {
      const result = await autofillMutation.mutateAsync()
      if (result.currentTitle) {
        setCurrentTitleOverride(result.currentTitle)
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

  function toPayload(values: OnboardingFormValues): CandidateProfileRequest {
    const trimmed = (value?: string) => (value && value.trim() !== '' ? value.trim() : undefined)
    const years = trimmed(values.yearsExperience)
    const salary = trimmed(values.desiredSalaryMinMillions)
    return {
      // 3 field co ban KHONG co o man nay - lay nguyen gia tri da nap (R-V3), tru currentTitle co
      // the bi "Dien tu CV" ghi de (R-A2).
      currentTitle: currentTitleOverride ?? profile?.currentTitle ?? undefined,
      location: profile?.location ?? undefined,
      dateOfBirth: profile?.dateOfBirth ?? undefined,
      headline: trimmed(values.headline),
      yearsExperience: years ? Number(years) : undefined,
      desiredIndustryCodes: values.desiredIndustryCodes,
      desiredLocationCodes: values.desiredLocationCodes,
      desiredWorkModes: values.desiredWorkModes,
      skills: values.skills,
      desiredSalaryMinMillions: salary ? Number(salary) : undefined,
      bio: trimmed(values.bio),
    }
  }

  const onSubmit = handleSubmit(async (values) => {
    setGeneralError(null)
    clearErrors(['desiredSalaryMinMillions', 'bio'])
    try {
      // R-O2 chong vong lap: setQueryData (trong onSuccess cua useSaveProfileMutation) chay xong
      // TRUOC khi mutateAsync() resolve - navigate() ben duoi luon thay cache da moi.
      await saveMutation.mutateAsync(toPayload(values))
      navigate('/candidate')
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 400) {
        const data = err.response.data as Record<string, unknown>
        if (data && typeof data === 'object' && !('error' in data)) {
          for (const [field, message] of Object.entries(data)) {
            if (typeof message === 'string' && isBeanValidationField(field)) {
              setError(field, { type: 'server', message })
            }
          }
          return
        }
      }
      setGeneralError(extractErrorMessage(err, 'Lưu thất bại, vui lòng thử lại.'))
    }
  })

  async function handleSkip() {
    setSkipError(null)
    try {
      await skipMutation.mutateAsync()
      navigate('/candidate')
    } catch {
      setSkipError('Không bỏ qua được, vui lòng thử lại.')
    }
  }

  const showFields = !isLoading && !isError
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
      <div className="mx-auto flex max-w-[800px] flex-col gap-6 px-4 py-8 md:px-6">
        <Card className="border border-m3-outline-variant bg-m3-surface text-m3-on-surface ring-0">
          <CardHeader className="flex flex-col gap-1">
            <CardTitle className="text-m3-headline-sm">Hoàn thiện hồ sơ</CardTitle>
            <p className="text-m3-body-md text-m3-on-surface-variant">
              Giúp chúng tôi gợi ý việc làm phù hợp hơn. Bạn có thể bỏ qua và điền sau ở trang Hồ sơ.
            </p>
          </CardHeader>
          <CardContent className="flex flex-col gap-6">
            {isLoading && <p className="text-m3-body-md text-m3-on-surface-variant">Đang tải...</p>}
            {isError && <p className="text-m3-body-md text-m3-error">Không tải được hồ sơ, vui lòng thử lại.</p>}

            {showFields && (
              <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6">
                <CareerPreferencesFields
                  idPrefix="onboarding"
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

                <div className="flex flex-wrap items-center justify-between gap-3 border-t border-m3-outline-variant pt-4">
                  <div className="flex flex-col gap-1">
                    <button
                      type="button"
                      onClick={handleSkip}
                      disabled={skipMutation.isPending}
                      className="flex h-10 items-center justify-center rounded-m3-button border border-m3-outline-variant px-4 text-m3-label-lg text-m3-on-surface disabled:cursor-not-allowed disabled:opacity-38"
                    >
                      {skipMutation.isPending ? 'Đang bỏ qua...' : 'Bỏ qua'}
                    </button>
                    {skipError && <p className="text-m3-body-sm text-m3-error">{skipError}</p>}
                  </div>
                  <div className="flex flex-col gap-1">
                    <button
                      type="submit"
                      disabled={isSubmitting || saveMutation.isPending}
                      className="flex h-10 items-center justify-center rounded-m3-button bg-m3-primary px-4 text-m3-label-lg text-m3-on-primary disabled:cursor-not-allowed disabled:opacity-38"
                    >
                      {saveMutation.isPending ? 'Đang lưu...' : 'Lưu'}
                    </button>
                    {generalError && <p className="text-m3-body-sm text-m3-error">{generalError}</p>}
                  </div>
                </div>
              </form>
            )}
          </CardContent>
        </Card>
      </div>
    </CandidateLayout>
  )
}

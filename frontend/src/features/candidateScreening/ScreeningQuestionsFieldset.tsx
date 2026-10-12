import type { RefObject } from 'react'
import { AlertCircle, Info } from 'lucide-react'
import type { Control, FieldErrors, UseFormRegister, UseFormSetValue } from 'react-hook-form'
import { Button } from '@/components/ui/button'
import type { ScreeningQuestionResponse } from '../screening/types'
import type { ApplyFormValues } from './applyFormSchema'
import { CANDIDATE_SCREENING_TEXT } from './candidateScreeningLabels'
import { ScreeningQuestionField } from './ScreeningQuestionField'

// Trang thai F1 do JobApplyForm tinh tu useScreeningFormQuery.
export type ScreeningFormStatus = 'loading' | 'error' | 'notAccepting' | 'ready'

interface ScreeningQuestionsFieldsetProps {
  status: ScreeningFormStatus
  questions: readonly ScreeningQuestionResponse[]
  // Cau 409 SCREENING_QUESTIONS_CHANGED cua backend; null khi khong co (UI.md 4b "Sau 409").
  conflictMessage: string | null
  headingRef: RefObject<HTMLHeadingElement | null>
  register: UseFormRegister<ApplyFormValues>
  setValue: UseFormSetValue<ApplyFormValues>
  control: Control<ApplyFormValues>
  errors: FieldErrors<ApplyFormValues>
  disabled: boolean
  onRetry: () => void
}

// Khoi "Cau hoi sang loc" cua form nop don (UI.md 4a, 4b). JobApplyForm KHONG render khoi nay khi tin co 0 cau (R-F1);
// hai vach ngan chi co khi khoi hien. Luu y trung tinh role="note" (khong m3-warning/m3-error, UI.md 5d).
export function ScreeningQuestionsFieldset({
  status,
  questions,
  conflictMessage,
  headingRef,
  register,
  setValue,
  control,
  errors,
  disabled,
  onRetry,
}: ScreeningQuestionsFieldsetProps) {
  return (
    <section
      aria-labelledby="apply-screening-heading"
      className="flex min-w-0 flex-col gap-4 border-y border-m3-outline-variant py-6"
    >
      <h2
        id="apply-screening-heading"
        ref={headingRef}
        tabIndex={-1}
        className="text-m3-title-md text-m3-on-surface outline-none focus-visible:ring-2 focus-visible:ring-m3-primary"
      >
        {CANDIDATE_SCREENING_TEXT.blockTitle}
      </h2>

      {conflictMessage && (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-m3-sm border border-m3-outline bg-m3-surface px-4 py-3 text-sm text-m3-on-surface"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          <p className="min-w-0 break-words">{conflictMessage}</p>
        </div>
      )}

      {status === 'loading' && (
        <div className="flex flex-col gap-4" aria-hidden="true">
          {[0, 1].map((i) => (
            <div key={i} className="flex flex-col gap-2">
              <div className="h-5 w-2/3 animate-pulse rounded-m3-xs bg-m3-surface-container" />
              <div className="h-4 w-1/3 animate-pulse rounded-m3-xs bg-m3-surface-container" />
              <div className="h-16 w-full animate-pulse rounded-m3-xs bg-m3-surface-container" />
            </div>
          ))}
        </div>
      )}

      {status === 'error' && (
        <div className="flex flex-col items-start gap-2">
          <p role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
            {CANDIDATE_SCREENING_TEXT.loadError}
          </p>
          <Button type="button" variant="outline" size="sm" onClick={onRetry}>
            {CANDIDATE_SCREENING_TEXT.retry}
          </Button>
        </div>
      )}

      {status === 'notAccepting' && (
        <p className="text-sm text-m3-on-surface">{CANDIDATE_SCREENING_TEXT.jobNotAccepting}</p>
      )}

      {status === 'ready' && (
        <>
          <div
            role="note"
            className="flex items-start gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container px-4 py-3 text-sm text-m3-on-surface"
          >
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-m3-on-surface" aria-hidden="true" />
            <p className="min-w-0 break-words">{CANDIDATE_SCREENING_TEXT.notice}</p>
          </div>
          <div className="flex flex-col gap-6">
            {questions.map((question, index) => (
              <ScreeningQuestionField
                key={question.id}
                question={question}
                position={index + 1}
                register={register}
                setValue={setValue}
                control={control}
                error={errors.answers?.[question.id]?.message}
                disabled={disabled}
              />
            ))}
          </div>
        </>
      )}
    </section>
  )
}

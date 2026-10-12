import { useWatch, type Control, type UseFormRegister, type UseFormSetValue } from 'react-hook-form'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { SCREENING_QUESTION_TYPE_LABELS, SCREENING_TEXT } from '../screening/screeningLabels'
import type { ScreeningQuestionResponse } from '../screening/types'
import { NO, YES, countAnswerChars, type ApplyFormValues } from './applyFormSchema'
import { CANDIDATE_SCREENING_TEXT } from './candidateScreeningLabels'

interface ScreeningQuestionFieldProps {
  question: ScreeningQuestionResponse
  position: number
  register: UseFormRegister<ApplyFormValues>
  setValue: UseFormSetValue<ApplyFormValues>
  control: Control<ApplyFormValues>
  error?: string
  disabled: boolean
}

// Mot cau hoi sang loc trong form nop don (UI.md 4a, 4d, muc 9): <fieldset> + <legend> "{n}. {noi dung}", dong
// "loai - bat buoc" bang chu (khong dau * do, khong badge). Tra loi ngan: Textarea + bo dem code point sau trim. Mot lua
// chon / Co-Khong: <input type="radio"> goc trong <label> (R-C7, khong them RadioGroup vao components/ui). "Bo chon" chi
// co o cau KHONG bat buoc khi da chon, de quay ve "khong tra loi". Khong to mau theo cau tra loi (R-N3).
export function ScreeningQuestionField({
  question,
  position,
  register,
  setValue,
  control,
  error,
  disabled,
}: ScreeningQuestionFieldProps) {
  const name = `answers.${question.id}` as const
  const value = useWatch({ control, name }) ?? ''
  const idPrefix = `screening-answer-${question.id}`
  const metaId = `${idPrefix}-meta`
  const errorId = `${idPrefix}-error`
  const counterId = `${idPrefix}-counter`
  const meta = `${SCREENING_QUESTION_TYPE_LABELS[question.questionType]} · ${
    question.required ? SCREENING_TEXT.required : SCREENING_TEXT.notRequired
  }`
  const describedBy = error ? `${metaId} ${errorId}` : metaId

  const choices =
    question.questionType === 'YES_NO'
      ? [
          { value: YES, label: CANDIDATE_SCREENING_TEXT.yes },
          { value: NO, label: CANDIDATE_SCREENING_TEXT.no },
        ]
      : question.options.map((option) => ({ value: option, label: option }))

  return (
    <fieldset className="flex min-w-0 flex-col gap-2" aria-describedby={describedBy} disabled={disabled}>
      <legend className="text-m3-title-md break-words text-m3-on-surface">
        {position}. {question.questionText}
      </legend>
      <p id={metaId} className="text-m3-body-sm text-m3-on-surface-variant">
        {meta}
      </p>

      {question.questionType === 'SHORT_TEXT' ? (
        <>
          <Textarea
            id={`${idPrefix}-text`}
            rows={3}
            aria-label={`${position}. ${question.questionText}`}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${counterId} ${errorId}` : counterId}
            {...register(name)}
          />
          <p id={counterId} className="self-end text-m3-body-sm text-m3-on-surface-variant">
            {CANDIDATE_SCREENING_TEXT.charCounter(countAnswerChars(value))}
          </p>
        </>
      ) : (
        <div
          className={
            question.questionType === 'YES_NO' ? 'flex flex-col sm:flex-row sm:gap-6' : 'flex flex-col'
          }
        >
          {choices.map((choice) => (
            <label
              key={choice.value}
              className="flex min-h-12 min-w-0 cursor-pointer items-center gap-3 sm:min-h-10"
            >
              <input
                type="radio"
                value={choice.value}
                className="size-4 shrink-0 accent-m3-primary"
                aria-invalid={error ? true : undefined}
                {...register(name)}
              />
              <span className="text-m3-body-md break-words text-m3-on-surface">{choice.label}</span>
            </label>
          ))}
        </div>
      )}

      {question.questionType !== 'SHORT_TEXT' && !question.required && value !== '' && (
        <Button
          type="button"
          variant="link"
          className="min-h-12 self-start px-0 text-m3-primary sm:min-h-0"
          aria-label={CANDIDATE_SCREENING_TEXT.clearAria(position)}
          onClick={() => setValue(name, '', { shouldDirty: true })}
        >
          {CANDIDATE_SCREENING_TEXT.clear}
        </Button>
      )}

      {error && (
        <p id={errorId} className="text-sm text-m3-error">
          {error}
        </p>
      )}
    </fieldset>
  )
}

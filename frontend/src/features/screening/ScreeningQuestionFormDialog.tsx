import { zodResolver } from '@hookform/resolvers/zod'
import { AlertCircle, Plus, X } from 'lucide-react'
import { useEffect } from 'react'
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { extractErrorMessage } from '../../lib/httpError'
import { QuestionTypeSegmentedControl } from './QuestionTypeSegmentedControl'
import { useAddScreeningQuestionMutation, useUpdateScreeningQuestionMutation } from './queries'
import {
  OPTIONS_MAX,
  OPTIONS_MIN,
  OPTION_TEXT_MAX,
  QUESTION_TEXT_MAX,
  SCREENING_FORM_ERRORS,
  SCREENING_QUESTION_TYPES,
  SCREENING_TEXT,
  countChars,
} from './screeningLabels'
import { ScreeningNotice } from './ScreeningNotice'
import type { ScreeningQuestionRequest, ScreeningQuestionResponse } from './types'

// Moi gioi han kiem tren gia tri DA BO khoang trang hai dau, dem theo code point - khop backend (R-Q4b). Phuong an chi
// kiem khi loai la SINGLE_CHOICE: o loai khac phan phuong an bi an va khong gui (UI.md 4c).
const questionSchema = z
  .object({
    questionType: z.enum(SCREENING_QUESTION_TYPES),
    questionText: z.string(),
    options: z.array(z.object({ value: z.string() })),
    required: z.boolean(),
  })
  .superRefine((values, ctx) => {
    const textLength = countChars(values.questionText)
    if (textLength === 0) {
      ctx.addIssue({ code: 'custom', path: ['questionText'], message: SCREENING_FORM_ERRORS.textEmpty })
    } else if (textLength > QUESTION_TEXT_MAX) {
      ctx.addIssue({ code: 'custom', path: ['questionText'], message: SCREENING_FORM_ERRORS.textTooLong })
    }

    if (values.questionType !== 'SINGLE_CHOICE') {
      return
    }
    if (values.options.length < OPTIONS_MIN) {
      ctx.addIssue({ code: 'custom', path: ['options', 'root'], message: SCREENING_FORM_ERRORS.optionsTooFew })
    } else if (values.options.length > OPTIONS_MAX) {
      ctx.addIssue({ code: 'custom', path: ['options', 'root'], message: SCREENING_FORM_ERRORS.optionsTooMany })
    }
    const seen = new Set<string>()
    let duplicate = false
    values.options.forEach((option, index) => {
      const trimmed = option.value.trim()
      if (trimmed.length === 0) {
        ctx.addIssue({ code: 'custom', path: ['options', index, 'value'], message: SCREENING_FORM_ERRORS.optionEmpty })
        return
      }
      if (countChars(trimmed) > OPTION_TEXT_MAX) {
        ctx.addIssue({
          code: 'custom',
          path: ['options', index, 'value'],
          message: SCREENING_FORM_ERRORS.optionTooLong,
        })
      }
      const key = trimmed.toLowerCase()
      if (seen.has(key)) {
        duplicate = true
      }
      seen.add(key)
    })
    if (duplicate) {
      ctx.addIssue({ code: 'custom', path: ['options', 'root'], message: SCREENING_FORM_ERRORS.optionDuplicate })
    }
  })

type QuestionFormValues = z.infer<typeof questionSchema>

const EMPTY_VALUES: QuestionFormValues = { questionType: 'SHORT_TEXT', questionText: '', options: [], required: false }

function toFormValues(question: ScreeningQuestionResponse | undefined): QuestionFormValues {
  if (!question) {
    return EMPTY_VALUES
  }
  return {
    questionType: question.questionType,
    questionText: question.questionText,
    options: question.options.map((value) => ({ value })),
    required: question.required,
  }
}

interface ScreeningQuestionFormDialogProps {
  jobId: string
  open: boolean
  onOpenChange: (open: boolean) => void
  question?: ScreeningQuestionResponse
}

export function ScreeningQuestionFormDialog({ jobId, open, onOpenChange, question }: ScreeningQuestionFormDialogProps) {
  const isEdit = Boolean(question)
  const addMutation = useAddScreeningQuestionMutation(jobId)
  const updateMutation = useUpdateScreeningQuestionMutation(jobId)
  const mutation = isEdit ? updateMutation : addMutation
  const idPrefix = question ? `screening-${question.id}` : 'screening-new'

  const {
    register,
    control,
    handleSubmit,
    reset,
    getValues,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<QuestionFormValues>({
    resolver: zodResolver(questionSchema),
    defaultValues: EMPTY_VALUES,
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'options' })
  const questionType = useWatch({ control, name: 'questionType' })
  const questionText = useWatch({ control, name: 'questionText' })

  // Moi lan mo: nap lai gia tri va xoa loi backend cua lan truoc.
  useEffect(() => {
    if (!open) return
    reset(toFormValues(question))
    addMutation.reset()
    updateMutation.reset()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, question])

  function handleTypeChange(type: QuestionFormValues['questionType']) {
    setValue('questionType', type)
    // Lan dau chuyen sang Mot lua chon: tao san 2 o trong. Quay lai sau khi da go thi giu nguyen cac o cu (UI.md 4c).
    if (type === 'SINGLE_CHOICE' && getValues('options').length === 0) {
      setValue('options', [{ value: '' }, { value: '' }])
    }
  }

  const onSubmit = handleSubmit(async (values) => {
    const payload: ScreeningQuestionRequest = {
      questionType: values.questionType,
      questionText: values.questionText.trim(),
      required: values.required,
      // Chuyen khoi Mot lua chon: KHONG gui options (UI.md 4c).
      ...(values.questionType === 'SINGLE_CHOICE' ? { options: values.options.map((o) => o.value.trim()) } : {}),
    }
    if (question) {
      await updateMutation.mutateAsync({ questionId: question.id, payload })
    } else {
      await addMutation.mutateAsync(payload)
    }
    onOpenChange(false)
  })

  const textErrorId = `${idPrefix}-text-error`
  const counterId = `${idPrefix}-text-counter`
  const typeLabelId = `${idPrefix}-type-label`
  const optionsLabelId = `${idPrefix}-options-label`
  const optionsError = errors.options?.root?.message ?? errors.options?.message
  const pending = isSubmitting || mutation.isPending

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? SCREENING_TEXT.dialogTitleEdit : SCREENING_TEXT.dialogTitleAdd}</DialogTitle>
          <ScreeningNotice compact />
        </DialogHeader>

        <form
          onSubmit={(event) => {
            // Bat loi da hien o cuoi hop thoai qua mutation.isError - khong de promise bi tu choi lot ra ngoai.
            onSubmit(event).catch(() => undefined)
          }}
          noValidate
          className="flex min-w-0 flex-col gap-4"
        >
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`${idPrefix}-text`}>{SCREENING_TEXT.fieldText}</Label>
            <Textarea
              id={`${idPrefix}-text`}
              rows={3}
              aria-invalid={errors.questionText ? true : undefined}
              aria-describedby={errors.questionText ? `${counterId} ${textErrorId}` : counterId}
              {...register('questionText')}
            />
            <p id={counterId} className="self-end text-sm text-m3-on-surface-variant">
              {SCREENING_TEXT.charCounter(countChars(questionText ?? ''))}
            </p>
            {errors.questionText && (
              <p id={textErrorId} className="text-sm text-m3-error">
                {errors.questionText.message}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label id={typeLabelId}>{SCREENING_TEXT.fieldType}</Label>
            <QuestionTypeSegmentedControl
              value={questionType}
              onChange={handleTypeChange}
              labelledBy={typeLabelId}
              disabled={pending}
            />
          </div>

          {questionType === 'SINGLE_CHOICE' && (
            <div role="group" aria-labelledby={optionsLabelId} className="flex flex-col gap-2">
              <Label id={optionsLabelId}>{SCREENING_TEXT.fieldOptions}</Label>
              {fields.map((field, index) => {
                const optionError = errors.options?.[index]?.value?.message
                const optionErrorId = `${idPrefix}-option-${index}-error`
                return (
                  <div key={field.id} className="flex flex-col gap-1">
                    <div className="flex items-center gap-2">
                      <Input
                        className="min-w-0 flex-1"
                        aria-label={`${SCREENING_TEXT.fieldOptions} ${index + 1}`}
                        aria-invalid={optionError ? true : undefined}
                        aria-describedby={optionError ? optionErrorId : undefined}
                        {...register(`options.${index}.value`)}
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        className="size-12 shrink-0 sm:size-7"
                        aria-label={SCREENING_TEXT.removeOption(index + 1)}
                        disabled={fields.length <= OPTIONS_MIN}
                        onClick={() => remove(index)}
                      >
                        <X />
                      </Button>
                    </div>
                    {optionError && (
                      <p id={optionErrorId} className="text-sm text-m3-error">
                        {optionError}
                      </p>
                    )}
                  </div>
                )
              })}
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="min-h-12 self-start sm:min-h-0"
                disabled={fields.length >= OPTIONS_MAX}
                onClick={() => append({ value: '' })}
              >
                <Plus /> {SCREENING_TEXT.addOption}
              </Button>
              {optionsError && <p className="text-sm text-m3-error">{optionsError}</p>}
            </div>
          )}

          <Controller
            control={control}
            name="required"
            render={({ field }) => (
              <div className="flex min-h-12 items-center gap-2 sm:min-h-10">
                <Checkbox
                  id={`${idPrefix}-required`}
                  checked={field.value}
                  onCheckedChange={(checked) => field.onChange(checked === true)}
                />
                <Label htmlFor={`${idPrefix}-required`} className="text-m3-on-surface">
                  {SCREENING_TEXT.fieldRequired}
                </Label>
              </div>
            )}
          />

          {/* Loi backend (INVALID_SCREENING_QUESTION, SCREENING_QUESTION_LIMIT_REACHED...) - dung cau message backend
              tra ve; backend khong tra ten truong nen hien o cuoi hop thoai (UI.md 4c, muc 6). */}
          {mutation.isError && (
            <div role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
              <p className="min-w-0 break-words">{extractErrorMessage(mutation.error, SCREENING_TEXT.saveError)}</p>
            </div>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {SCREENING_TEXT.cancel}
            </Button>
            <Button type="submit" disabled={pending}>
              {pending ? SCREENING_TEXT.saving : SCREENING_TEXT.save}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

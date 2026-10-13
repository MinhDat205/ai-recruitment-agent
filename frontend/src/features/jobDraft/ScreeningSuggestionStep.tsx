import { AlertCircle, Plus, Sparkles, X } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { QuestionTypeSegmentedControl } from '../screening/QuestionTypeSegmentedControl'
import {
  OPTIONS_MAX,
  SCREENING_QUESTION_TYPE_LABELS,
  SCREENING_TEXT,
  countChars,
} from '../screening/screeningLabels'
import { ScreeningNotice } from '../screening/ScreeningNotice'
import type { ScreeningQuestionType } from '../screening/types'
import { PartStepStatus } from './AiDraftInputPanel'
import { AiSuggestedMark } from './AiSuggestedMark'
import { JOB_DRAFT_TEXT, MAX_KEPT_QUESTIONS } from './jobDraftLabels'
import { focusSoon, questionCardId, questionEditErrors, type QuestionEditErrors } from './jobDraftValidation'
import type { JobAiDraftState, KeptQuestion } from './useJobAiDraft'

// UI.md 4g - buoc 4 "Cau hoi sang loc" (R-Q). Luu y nhan than cua FR-H10 LUON hien dau buoc (R-Q3). Goi y chua chon mac
// dinh (Q12); "Bat buoc tra loi" KHONG tick san (R-Q1). Thu tu cau = thu tu giu; khong keo tha.
export function ScreeningSuggestionStep({
  draft,
  serverErrors,
}: {
  draft: JobAiDraftState
  // Loi J2 theo so thu tu cau hoi (0-based).
  serverErrors: Record<number, string>
}) {
  const state = draft.parts.QUESTIONS
  const kept = draft.keptQuestions
  const limitReached = kept.length >= MAX_KEPT_QUESTIONS

  // Sau "Giu" - focus the cau vua giu (UI.md muc 9).
  function keep(ids: string[]) {
    const newIds = draft.keepQuestions(ids)
    if (newIds.length > 0) {
      focusSoon(questionCardId(newIds[0]))
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <ScreeningNotice />

      <PartStepStatus
        part="QUESTIONS"
        state={state}
        disabled={draft.isGenerating}
        onRetry={(parts) => void draft.generate(parts)}
      />

      {state.hadSuccess && (
        <div className="flex flex-col gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container-high p-3 sm:p-4">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <p className="flex items-center gap-1.5 text-sm font-medium text-m3-on-surface">
              <Sparkles className="h-4 w-4 shrink-0" aria-hidden="true" />
              {JOB_DRAFT_TEXT.aiBlockLabel} · {JOB_DRAFT_TEXT.questionSuggestions}
            </p>
            <Button
              type="button"
              variant="outline"
              className="min-h-12 w-full sm:min-h-10 sm:w-auto"
              disabled={draft.isGenerating}
              onClick={() => void draft.generate(['QUESTIONS'])}
            >
              {JOB_DRAFT_TEXT.regenerate}
            </Button>
          </div>
          {draft.suggestedCounts.QUESTIONS === 0 && (
            <p className="text-sm break-words text-m3-on-surface">{JOB_DRAFT_TEXT.questionsEmpty}</p>
          )}
          {draft.pendingQuestions.length > 0 && (
            <ul className="flex flex-col divide-y divide-m3-outline-variant">
              {draft.pendingQuestions.map((question) => (
                <li key={question.id} className="flex flex-col gap-2 py-2 sm:flex-row sm:items-start sm:justify-between">
                  <div className="flex min-w-0 flex-col gap-1 text-sm text-m3-on-surface">
                    <p className="break-words">
                      {SCREENING_QUESTION_TYPE_LABELS[question.questionType]} · {question.questionText}
                    </p>
                    {question.questionType === 'SINGLE_CHOICE' && question.options.length > 0 && (
                      <ul className="ml-4 list-disc">
                        {question.options.map((option) => (
                          <li key={option} className="break-words">
                            {option}
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <Button
                      type="button"
                      className="min-h-12 flex-1 sm:min-h-10 sm:flex-none"
                      disabled={limitReached}
                      aria-label={JOB_DRAFT_TEXT.keepQuestionLabel(question.questionText)}
                      onClick={() => keep([question.id])}
                    >
                      {JOB_DRAFT_TEXT.keep}
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      className="min-h-12 flex-1 sm:min-h-10 sm:flex-none"
                      aria-label={JOB_DRAFT_TEXT.discardQuestionLabel(question.questionText)}
                      onClick={() => draft.discardPendingQuestion(question.id)}
                    >
                      {JOB_DRAFT_TEXT.discard}
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
          {draft.pendingQuestions.length > 0 && (
            <div className="flex flex-col sm:items-end">
              <Button
                type="button"
                variant="outline"
                className="min-h-12 w-full sm:min-h-10 sm:w-auto"
                disabled={limitReached}
                onClick={() => keep(draft.pendingQuestions.map((q) => q.id))}
              >
                {JOB_DRAFT_TEXT.keepAll}
              </Button>
            </div>
          )}
          {limitReached && <p className="text-sm text-m3-on-surface">{JOB_DRAFT_TEXT.questionsLimit}</p>}
        </div>
      )}

      {kept.length > 0 && (
        <div className="flex flex-col gap-2">
          <h3 className="text-sm font-medium text-m3-on-surface">{JOB_DRAFT_TEXT.keptQuestions(kept.length)}</h3>
          <ol className="flex flex-col gap-2">
            {kept.map((question, index) => (
              <KeptQuestionCard
                key={question.id}
                index={index}
                question={question}
                serverError={serverErrors[index]}
                onChange={(update) => draft.updateKeptQuestion(question.id, update)}
                onRemove={() => draft.removeKeptQuestion(question.id)}
              />
            ))}
          </ol>
        </div>
      )}
    </div>
  )
}

function KeptQuestionCard({
  index,
  question,
  serverError,
  onChange,
  onRemove,
}: {
  index: number
  question: KeptQuestion
  serverError: string | undefined
  onChange: (update: Partial<Omit<KeptQuestion, 'id'>>) => void
  onRemove: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [type, setType] = useState<ScreeningQuestionType>(question.questionType)
  const [text, setText] = useState(question.questionText)
  const [options, setOptions] = useState<string[]>(question.options)
  const [errors, setErrors] = useState<QuestionEditErrors | null>(null)
  const cardId = questionCardId(question.id)
  const markId = `${cardId}-ai`

  function startEdit() {
    setType(question.questionType)
    setText(question.questionText)
    setOptions(question.questionType === 'SINGLE_CHOICE' ? question.options : [])
    setErrors(null)
    setEditing(true)
  }

  function changeType(next: ScreeningQuestionType) {
    setType(next)
    if (next === 'SINGLE_CHOICE' && options.length === 0) {
      setOptions(['', ''])
    }
  }

  function finishEdit() {
    const usedOptions = type === 'SINGLE_CHOICE' ? options : []
    const problems = questionEditErrors(type, text, usedOptions)
    if (problems) {
      setErrors(problems)
      return
    }
    onChange({
      questionType: type,
      questionText: text.trim(),
      options: usedOptions.map((option) => option.trim()),
      aiEdited: true,
    })
    setEditing(false)
  }

  return (
    <li
      id={cardId}
      tabIndex={-1}
      className="flex flex-col gap-2 rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-3 text-sm text-m3-on-surface focus-visible:outline-2 focus-visible:outline-m3-primary"
    >
      {editing ? (
        <div className="flex flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <span id={`${cardId}-type`} className="text-sm font-medium">
              {SCREENING_TEXT.fieldType}
            </span>
            <QuestionTypeSegmentedControl value={type} onChange={changeType} labelledBy={`${cardId}-type`} />
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor={`${cardId}-text`}>{SCREENING_TEXT.fieldText}</Label>
            <Textarea
              id={`${cardId}-text`}
              rows={2}
              value={text}
              aria-invalid={errors?.text ? true : undefined}
              aria-describedby={`${cardId}-text-counter`}
              onChange={(event) => setText(event.target.value)}
            />
            <p id={`${cardId}-text-counter`} className="self-end text-sm text-m3-on-surface-variant">
              {SCREENING_TEXT.charCounter(countChars(text))}
            </p>
            {errors?.text && <p className="text-sm text-m3-error">{errors.text}</p>}
          </div>
          {type === 'SINGLE_CHOICE' && (
            <div className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">{SCREENING_TEXT.fieldOptions}</span>
              {options.map((option, optionIndex) => (
                <div key={optionIndex} className="flex flex-col gap-1">
                  <div className="flex items-center gap-2">
                    <Input
                      aria-label={`${SCREENING_TEXT.fieldOptions} ${optionIndex + 1}`}
                      value={option}
                      aria-invalid={errors?.optionItems[optionIndex] ? true : undefined}
                      onChange={(event) =>
                        setOptions((current) =>
                          current.map((value, i) => (i === optionIndex ? event.target.value : value)),
                        )
                      }
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="min-h-10 min-w-10"
                      aria-label={SCREENING_TEXT.removeOption(optionIndex + 1)}
                      onClick={() => setOptions((current) => current.filter((_, i) => i !== optionIndex))}
                    >
                      <X aria-hidden="true" />
                    </Button>
                  </div>
                  {errors?.optionItems[optionIndex] && (
                    <p className="text-sm text-m3-error">{errors.optionItems[optionIndex]}</p>
                  )}
                </div>
              ))}
              {options.length < OPTIONS_MAX && (
                <Button
                  type="button"
                  variant="outline"
                  className="min-h-10 self-start"
                  onClick={() => setOptions((current) => [...current, ''])}
                >
                  <Plus aria-hidden="true" />
                  {SCREENING_TEXT.addOption}
                </Button>
              )}
              {errors?.options && <p className="text-sm text-m3-error">{errors.options}</p>}
            </div>
          )}
          <div className="flex gap-2 sm:justify-end">
            <Button type="button" className="min-h-12 flex-1 sm:min-h-10 sm:flex-none" onClick={finishEdit}>
              {JOB_DRAFT_TEXT.doneEditing}
            </Button>
            <Button
              type="button"
              variant="ghost"
              className="min-h-12 flex-1 sm:min-h-10 sm:flex-none"
              onClick={() => setEditing(false)}
            >
              {JOB_DRAFT_TEXT.cancel}
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-1">
          <p className="break-words">
            {index + 1}. {SCREENING_QUESTION_TYPE_LABELS[question.questionType]} · {question.questionText}
          </p>
          {question.questionType === 'SINGLE_CHOICE' && question.options.length > 0 && (
            <ul className="ml-4 list-disc">
              {question.options.map((option) => (
                <li key={option} className="break-words">
                  {option}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {!editing && (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
            <div className="flex min-h-10 items-center gap-2">
              <Checkbox
                id={`${cardId}-required`}
                checked={question.required}
                aria-describedby={question.aiEdited ? undefined : markId}
                onCheckedChange={(checked) => onChange({ required: checked === true })}
              />
              <Label htmlFor={`${cardId}-required`} className="text-m3-on-surface">
                {JOB_DRAFT_TEXT.requiredAnswer}
              </Label>
            </div>
            {!question.aiEdited && <AiSuggestedMark id={markId} />}
          </div>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              className="min-h-12 flex-1 sm:min-h-10 sm:flex-none"
              onClick={startEdit}
            >
              {JOB_DRAFT_TEXT.edit}
            </Button>
            <Button type="button" variant="ghost" className="min-h-12 flex-1 sm:min-h-10 sm:flex-none" onClick={onRemove}>
              {JOB_DRAFT_TEXT.discard}
            </Button>
          </div>
        </div>
      )}
      {serverError && (
        <p role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          {serverError}
        </p>
      )}
    </li>
  )
}

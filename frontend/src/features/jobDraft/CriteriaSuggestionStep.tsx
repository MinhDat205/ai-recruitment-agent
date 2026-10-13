import { AlertCircle, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { PartStepStatus } from './AiDraftInputPanel'
import { AiSuggestedMark } from './AiSuggestedMark'
import { JOB_DRAFT_TEXT, MAX_KEPT_CRITERIA } from './jobDraftLabels'
import { criterionNameError, focusSoon, totalWeight, weightError, weightInputId } from './jobDraftValidation'
import type { JobAiDraftState, KeptCriterion } from './useJobAiDraft'

function formatTotal(total: number): string {
  return Number.isInteger(total) ? String(total) : total.toFixed(2)
}

// UI.md 4e - buoc 3 "Tieu chi danh gia" (R-C, R-W). Goi y CHUA CHON mac dinh (Q12); o trong so TRONG, khong placeholder
// so, khong chia deu (R-W1). Khong co nut tu them tieu chi (REQUIREMENT muc 6). Khong mau theo nguong cho tong.
export function CriteriaSuggestionStep({
  draft,
  showAllErrors,
  serverErrors,
}: {
  draft: JobAiDraftState
  // Sau lan bam "Luu nhap" - hien moi loi o trong so du chua cham.
  showAllErrors: boolean
  // Loi J2 theo so thu tu tieu chi (0-based) - hien tai dung the.
  serverErrors: Record<number, string>
}) {
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const state = draft.parts.CRITERIA
  const kept = draft.keptCriteria
  const limitReached = kept.length >= MAX_KEPT_CRITERIA
  const total = totalWeight(kept)

  // Sau "Giu" - focus o trong so cua tieu chi vua giu (UI.md muc 9).
  function keep(ids: string[]) {
    const newIds = draft.keepCriteria(ids)
    if (newIds.length > 0) {
      focusSoon(weightInputId(newIds[0]))
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm break-words text-m3-on-surface-variant">{JOB_DRAFT_TEXT.criteriaIntro}</p>

      <PartStepStatus
        part="CRITERIA"
        state={state}
        disabled={draft.isGenerating}
        onRetry={(parts) => void draft.generate(parts)}
      />

      {state.hadSuccess && (
        <div className="flex flex-col gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container-high p-3 sm:p-4">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <p className="flex items-center gap-1.5 text-sm font-medium text-m3-on-surface">
              <Sparkles className="h-4 w-4 shrink-0" aria-hidden="true" />
              {JOB_DRAFT_TEXT.aiBlockLabel} · {JOB_DRAFT_TEXT.criteriaSuggestions}
            </p>
            <Button
              type="button"
              variant="outline"
              className="min-h-12 w-full sm:min-h-10 sm:w-auto"
              disabled={draft.isGenerating}
              onClick={() => void draft.generate(['CRITERIA'])}
            >
              {JOB_DRAFT_TEXT.regenerate}
            </Button>
          </div>
          {draft.suggestedCounts.CRITERIA === 0 && (
            <p className="text-sm break-words text-m3-on-surface">{JOB_DRAFT_TEXT.criteriaEmpty}</p>
          )}
          {draft.pendingCriteria.length > 0 && (
            <ul className="flex flex-col divide-y divide-m3-outline-variant">
              {draft.pendingCriteria.map((criterion) => (
                <li key={criterion.id} className="flex flex-col gap-2 py-2 sm:flex-row sm:items-start sm:justify-between">
                  <div className="flex min-w-0 flex-col gap-0.5 text-sm text-m3-on-surface">
                    <p className="font-medium break-words">{criterion.name}</p>
                    {criterion.description && <p className="break-words">{criterion.description}</p>}
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <Button
                      type="button"
                      className="min-h-12 flex-1 sm:min-h-10 sm:flex-none"
                      disabled={limitReached}
                      aria-label={JOB_DRAFT_TEXT.keepCriterionLabel(criterion.name)}
                      onClick={() => keep([criterion.id])}
                    >
                      {JOB_DRAFT_TEXT.keep}
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      className="min-h-12 flex-1 sm:min-h-10 sm:flex-none"
                      aria-label={JOB_DRAFT_TEXT.discardCriterionLabel(criterion.name)}
                      onClick={() => draft.discardPendingCriterion(criterion.id)}
                    >
                      {JOB_DRAFT_TEXT.discard}
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
          {draft.pendingCriteria.length > 0 && (
            <div className="flex flex-col gap-1 sm:items-end">
              <Button
                type="button"
                variant="outline"
                className="min-h-12 w-full sm:min-h-10 sm:w-auto"
                disabled={limitReached}
                onClick={() => keep(draft.pendingCriteria.map((c) => c.id))}
              >
                {JOB_DRAFT_TEXT.keepAll}
              </Button>
            </div>
          )}
          {limitReached && <p className="text-sm text-m3-on-surface">{JOB_DRAFT_TEXT.criteriaLimit}</p>}
        </div>
      )}

      {kept.length > 0 && (
        <div className="flex flex-col gap-2">
          <h3 className="text-sm font-medium text-m3-on-surface">{JOB_DRAFT_TEXT.keptCriteria(kept.length)}</h3>
          <ul className="flex flex-col gap-2">
            {kept.map((criterion, index) => (
              <KeptCriterionCard
                key={criterion.id}
                criterion={criterion}
                kept={kept}
                showWeightError={showAllErrors || Boolean(touched[criterion.id])}
                serverError={serverErrors[index]}
                onWeightBlur={() => setTouched((current) => ({ ...current, [criterion.id]: true }))}
                onChange={(update) => draft.updateKeptCriterion(criterion.id, update)}
                onRemove={() => draft.removeKeptCriterion(criterion.id)}
              />
            ))}
          </ul>
        </div>
      )}

      <div className="flex flex-col gap-1">
        <p className="text-sm text-m3-on-surface">{JOB_DRAFT_TEXT.totalWeight(formatTotal(total))}</p>
        {total > 100 && (
          <p role="alert" className="text-sm text-m3-error">
            {JOB_DRAFT_TEXT.weightTotalExceeded}
          </p>
        )}
        {serverErrors[-1] && (
          <p role="alert" className="text-sm break-words text-m3-error">
            {serverErrors[-1]}
          </p>
        )}
      </div>
    </div>
  )
}

function KeptCriterionCard({
  criterion,
  kept,
  showWeightError,
  serverError,
  onWeightBlur,
  onChange,
  onRemove,
}: {
  criterion: KeptCriterion
  kept: KeptCriterion[]
  showWeightError: boolean
  serverError: string | undefined
  onWeightBlur: () => void
  onChange: (update: Partial<Pick<KeptCriterion, 'name' | 'description' | 'weight'>>) => void
  onRemove: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [nameDraft, setNameDraft] = useState(criterion.name)
  const [descriptionDraft, setDescriptionDraft] = useState(criterion.description)
  const [nameError, setNameError] = useState<string | null>(null)
  const inputId = weightInputId(criterion.id)
  const errorId = `${inputId}-error`
  const markId = `${inputId}-ai`
  const fromAi = criterion.name === criterion.aiName && criterion.description === criterion.aiDescription
  const error = showWeightError ? weightError(criterion.weight) : null

  function startEdit() {
    setNameDraft(criterion.name)
    setDescriptionDraft(criterion.description)
    setNameError(null)
    setEditing(true)
  }

  function finishEdit() {
    const problem = criterionNameError(nameDraft, criterion.id, kept)
    if (problem) {
      setNameError(problem)
      return
    }
    onChange({ name: nameDraft.trim(), description: descriptionDraft.trim() })
    setEditing(false)
  }

  return (
    <li className="flex flex-col gap-2 rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-3 text-sm text-m3-on-surface">
      {editing ? (
        <div className="flex flex-col gap-2">
          <div className="flex flex-col gap-1">
            <Label htmlFor={`${inputId}-name`}>{JOB_DRAFT_TEXT.criterionNameLabel}</Label>
            <Input
              id={`${inputId}-name`}
              value={nameDraft}
              aria-invalid={nameError ? true : undefined}
              aria-describedby={nameError ? `${inputId}-name-error` : undefined}
              onChange={(event) => setNameDraft(event.target.value)}
            />
            {nameError && (
              <p id={`${inputId}-name-error`} className="text-sm text-m3-error">
                {nameError}
              </p>
            )}
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor={`${inputId}-description`}>{JOB_DRAFT_TEXT.criterionDescriptionLabel}</Label>
            <Textarea
              id={`${inputId}-description`}
              rows={2}
              value={descriptionDraft}
              onChange={(event) => setDescriptionDraft(event.target.value)}
            />
          </div>
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
        <div className="flex flex-col gap-0.5">
          <p className="flex flex-wrap items-center gap-x-2 font-medium break-words">
            <span>{criterion.name}</span>
            {fromAi && <AiSuggestedMark id={markId} />}
          </p>
          {criterion.description && <p className="break-words">{criterion.description}</p>}
        </div>
      )}

      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-1">
          <Label htmlFor={inputId}>
            {JOB_DRAFT_TEXT.weightLabel}
            <span className="sr-only"> — {criterion.name}</span>
          </Label>
          <Input
            id={inputId}
            type="number"
            min={0.01}
            max={100}
            step={0.01}
            inputMode="decimal"
            className="w-full sm:w-32"
            value={criterion.weight}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
            onChange={(event) => onChange({ weight: event.target.value })}
            onBlur={onWeightBlur}
          />
        </div>
        {!editing && (
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
        )}
      </div>
      {error && (
        <p id={errorId} className="text-sm text-m3-error">
          {error}
        </p>
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

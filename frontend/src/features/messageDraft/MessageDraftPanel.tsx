import { useEffect, useId, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { AlertCircle, Sparkles, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { extractErrorMessage } from '../../lib/httpError'
import {
  CUSTOM_PURPOSE_PLACEHOLDERS,
  DRAFT_SCENARIO_LABELS,
  DRAFT_TEXT,
  DRAFT_TONE_LABELS,
  DRAFT_UNAVAILABLE_REASON_LABELS,
  MAX_CUSTOM_PURPOSE_LENGTH,
} from './draftLabels'
import { errorCodeOf, isClientError, useCreateMessageDraftMutation, useDraftScenariosQuery } from './queries'
import { ToneSegmentedControl } from './ToneSegmentedControl'
import type { DraftScenario, DraftSelection, DraftSide, ScenarioOption } from './types'

interface DraftError {
  message: string
  // "Thu lai" chi co voi loi tam thoi (502/503/504/429/mang) - UI.md muc 6.
  retryable: boolean
}

// Tinh huong dang chon thuc su: lua chon da luu neu con dung duoc, neu khong thi tinh huong DAU TIEN con dung duoc theo
// thu tu A1 (UI.md muc 4b). null khi A1 chua tai xong.
function effectiveScenario(options: ScenarioOption[] | undefined, selected: DraftScenario | null): DraftScenario | null {
  if (!options) {
    return null
  }
  const current = options.find((option) => option.scenario === selected)
  if (current?.available) {
    return current.scenario
  }
  return options.find((option) => option.available)?.scenario ?? null
}

// FR-C07 - khoi soan nhap dung chung hai phia, nam TRONG MessageComposer, phia tren o nhap (UI.md muc 4b-4d, 4g, 6).
// Khong hop thoai/lop phu: nguoi dung van go duoc vao o nhap trong luc cho. Ban nhap chi nam trong state cua khoi nay
// (R-D1); chi vao o nhap khi bam "Dung ban nhap" (onUseDraft) - khong tu dien, khong tu gui (R-D2). Dong khoi (unmount)
// thi ket qua A2 ve sau bi bo (requestSeq).
export function MessageDraftPanel({
  side,
  applicationId,
  panelId,
  selection,
  onSelectionChange,
  onUseDraft,
  onClose,
  onReadOnlyConflict,
}: {
  side: DraftSide
  applicationId: string
  panelId: string
  selection: DraftSelection
  onSelectionChange: (selection: DraftSelection) => void
  onUseDraft: (draft: string) => void
  onClose: () => void
  onReadOnlyConflict: (message: string) => void
}) {
  const scenariosQuery = useDraftScenariosQuery(side, applicationId)
  const draftMutation = useCreateMessageDraftMutation(side, applicationId)
  const [draft, setDraft] = useState<string | null>(null)
  const [draftError, setDraftError] = useState<DraftError | null>(null)
  const [editingChoices, setEditingChoices] = useState(false)
  const requestSeq = useRef(0)
  const checkedRadioRef = useRef<HTMLInputElement | null>(null)
  const focusedOnOpen = useRef(false)
  const handledReadOnlyError = useRef<unknown>(null)

  const baseId = useId()
  const titleId = `${baseId}-title`
  const radioName = `${baseId}-scenario`
  const purposeId = `${baseId}-purpose`
  const purposeCounterId = `${baseId}-purpose-counter`

  const options = scenariosQuery.data?.scenarios
  const scenario = effectiveScenario(options, selection.scenario)
  const isDrafting = draftMutation.isPending
  const purposeLength = selection.customPurpose.length
  const purposeTooLong = purposeLength > MAX_CUSTOM_PURPOSE_LENGTH
  const purposeInvalid = scenario === 'CUSTOM' && (selection.customPurpose.trim().length === 0 || purposeTooLong)
  const canGenerate = scenario !== null && !isDrafting && !purposeInvalid
  const showChoices = draft === null || editingChoices

  // Ket qua A2 ve sau khi khoi dong bi bo.
  useEffect(() => {
    const seq = requestSeq
    return () => {
      seq.current += 1
    }
  }, [])

  // Mo khoi thi focus vao radio dang chon, mot lan, khi A1 tai xong (UI.md muc 9).
  useEffect(() => {
    if (options && !focusedOnOpen.current) {
      focusedOnOpen.current = true
      checkedRadioRef.current?.focus()
    }
  }, [options])

  // A1 tra 409 CONVERSATION_READ_ONLY: xu ly nhu loi 409 khi gui cua C06 (khung soan bien mat) - UI.md muc 6.
  useEffect(() => {
    const error = scenariosQuery.error
    if (error && error !== handledReadOnlyError.current && errorCodeOf(error) === 'CONVERSATION_READ_ONLY') {
      handledReadOnlyError.current = error
      onReadOnlyConflict(extractErrorMessage(error, DRAFT_TEXT.conversationNotFound))
    }
  }, [scenariosQuery.error, onReadOnlyConflict])

  async function generate() {
    if (!canGenerate || scenario === null) {
      return
    }
    const seq = ++requestSeq.current
    setDraftError(null)
    setDraft(null)
    try {
      const result = await draftMutation.mutateAsync({
        scenario,
        tone: selection.tone,
        ...(scenario === 'CUSTOM' ? { customPurpose: selection.customPurpose } : {}),
      })
      if (seq !== requestSeq.current) {
        return
      }
      setDraft(result.draft)
      setEditingChoices(false)
    } catch (err) {
      if (seq !== requestSeq.current) {
        return
      }
      handleDraftError(err)
    }
  }

  function handleDraftError(err: unknown) {
    const code = errorCodeOf(err)
    if (code === 'CONVERSATION_READ_ONLY') {
      onReadOnlyConflict(extractErrorMessage(err, DRAFT_TEXT.draftFailedFallback))
      return
    }
    const message = extractErrorMessage(err, DRAFT_TEXT.draftFailedFallback)
    if (code === 'DRAFT_SCENARIO_UNAVAILABLE') {
      // Trang thai don doi giua chung - tai lai A1 de cap nhat khoa.
      setDraftError({ message, retryable: false })
      void scenariosQuery.refetch()
      return
    }
    const status = isAxiosError(err) ? err.response?.status : undefined
    const retryable = status === undefined || status === 429 || status >= 500
    setDraftError({ message, retryable })
  }

  function dismissDraft() {
    setDraft(null)
    setEditingChoices(false)
  }

  function renderScenarioError() {
    const error = scenariosQuery.error
    if (errorCodeOf(error) === 'CONVERSATION_READ_ONLY') {
      return null
    }
    if (isClientError(error)) {
      return <p className="text-sm break-words text-m3-on-surface">{DRAFT_TEXT.conversationNotFound}</p>
    }
    return (
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          {DRAFT_TEXT.scenariosLoadFailed}
        </p>
        <Button
          type="button"
          variant="outline"
          className="min-h-10 w-full sm:w-auto"
          onClick={() => void scenariosQuery.refetch()}
        >
          {DRAFT_TEXT.retry}
        </Button>
      </div>
    )
  }

  function renderChoices(scenarioOptions: ScenarioOption[]) {
    return (
      <div className="flex flex-col gap-3">
        <fieldset disabled={isDrafting} className="flex min-w-0 flex-col gap-1">
          <legend className="mb-1 text-sm font-medium text-m3-on-surface">{DRAFT_TEXT.scenarioLegend}</legend>
          {scenarioOptions.map((option) => {
            const inputId = `${baseId}-${option.scenario}`
            const reasonId = `${inputId}-reason`
            const checked = option.scenario === scenario
            return (
              <div key={option.scenario} className="flex flex-col">
                <label htmlFor={inputId} className="flex min-h-10 items-center gap-2">
                  <input
                    id={inputId}
                    ref={checked ? checkedRadioRef : undefined}
                    type="radio"
                    name={radioName}
                    value={option.scenario}
                    checked={checked}
                    disabled={!option.available}
                    onChange={() => onSelectionChange({ ...selection, scenario: option.scenario })}
                    aria-describedby={option.unavailableReason ? reasonId : undefined}
                    className="h-4 w-4 shrink-0 accent-m3-primary"
                  />
                  <span
                    className={`text-sm break-words ${
                      option.available ? 'text-m3-on-surface' : 'text-m3-on-surface-variant'
                    }`}
                  >
                    {DRAFT_SCENARIO_LABELS[option.scenario]}
                  </span>
                </label>
                {option.unavailableReason && (
                  <p id={reasonId} className="ml-6 text-sm break-words text-m3-on-surface-variant">
                    {DRAFT_UNAVAILABLE_REASON_LABELS[option.unavailableReason]}
                  </p>
                )}
                {option.scenario === 'CUSTOM' && checked && (
                  <div className="mt-1 ml-6 flex flex-col gap-1">
                    <label htmlFor={purposeId} className="sr-only">
                      {DRAFT_TEXT.customPurposeLabel}
                    </label>
                    <Textarea
                      id={purposeId}
                      value={selection.customPurpose}
                      onChange={(event) => onSelectionChange({ ...selection, customPurpose: event.target.value })}
                      placeholder={CUSTOM_PURPOSE_PLACEHOLDERS[side]}
                      aria-describedby={purposeCounterId}
                      aria-invalid={purposeTooLong || undefined}
                      className="min-h-16 break-words"
                    />
                    <span
                      id={purposeCounterId}
                      className={`self-end text-sm ${purposeTooLong ? 'text-m3-error' : 'text-m3-on-surface-variant'}`}
                    >
                      {purposeLength}/{MAX_CUSTOM_PURPOSE_LENGTH}
                    </span>
                  </div>
                )}
              </div>
            )
          })}
        </fieldset>

        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-3">
          <span className="text-sm font-medium text-m3-on-surface" aria-hidden="true">
            {DRAFT_TEXT.toneLabel}
          </span>
          <ToneSegmentedControl
            value={selection.tone}
            onChange={(tone) => onSelectionChange({ ...selection, tone })}
            disabled={isDrafting}
          />
        </div>
      </div>
    )
  }

  return (
    <section
      id={panelId}
      aria-labelledby={titleId}
      aria-busy={isDrafting || scenariosQuery.isPending}
      className="flex flex-col gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-3 sm:p-4"
    >
      <div className="flex items-center justify-between gap-2">
        <h3 id={titleId} className="text-m3-title-md text-m3-on-surface">
          {DRAFT_TEXT.panelTitle}
        </h3>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="min-h-10 min-w-10"
          aria-label={DRAFT_TEXT.closeButtonLabel}
          onClick={onClose}
        >
          <X aria-hidden="true" />
        </Button>
      </div>

      {scenariosQuery.isPending && (
        <div className="flex flex-col gap-2" aria-hidden="true">
          {[0, 1, 2, 3, 4].map((line) => (
            <div
              key={line}
              className="h-5 w-full animate-pulse rounded-m3-xs bg-m3-surface-container-highest motion-reduce:animate-none"
            />
          ))}
        </div>
      )}

      {scenariosQuery.isError && renderScenarioError()}

      <div aria-live="polite" className="flex flex-col gap-3">
        {draft !== null && (
          <>
            <div className="flex flex-col gap-2 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container-high p-3">
              <p className="flex items-center gap-1.5 text-sm font-medium text-m3-on-surface">
                <Sparkles className="h-4 w-4 shrink-0" aria-hidden="true" />
                {DRAFT_TEXT.aiBadge}
              </p>
              {/* Van ban thuan, giu xuong dong, khong HTML/Markdown (UI.md muc 4d, 10). */}
              <p className="text-sm whitespace-pre-wrap break-words text-m3-on-surface">{draft}</p>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
              <Button type="button" className="min-h-10 w-full sm:w-auto" onClick={() => onUseDraft(draft)}>
                {DRAFT_TEXT.useDraft}
              </Button>
              <Button
                type="button"
                variant="outline"
                className="min-h-10 w-full sm:w-auto"
                onClick={() => void generate()}
                disabled={!canGenerate}
              >
                {DRAFT_TEXT.regenerate}
              </Button>
              <Button type="button" variant="ghost" className="min-h-10 w-full sm:w-auto" onClick={dismissDraft}>
                {DRAFT_TEXT.dismiss}
              </Button>
            </div>
          </>
        )}

        {draftError && (
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <p role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
              {draftError.message}
            </p>
            {draftError.retryable && (
              <Button
                type="button"
                variant="outline"
                className="min-h-10 w-full sm:w-auto"
                onClick={() => void generate()}
                disabled={!canGenerate}
              >
                {DRAFT_TEXT.retry}
              </Button>
            )}
          </div>
        )}
      </div>

      {options &&
        (showChoices ? (
          renderChoices(options)
        ) : (
          <p className="flex flex-wrap items-center gap-x-2 text-sm break-words text-m3-on-surface-variant">
            {scenario && DRAFT_SCENARIO_LABELS[scenario]} · {DRAFT_TONE_LABELS[selection.tone]}
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="min-h-10"
              onClick={() => setEditingChoices(true)}
            >
              {DRAFT_TEXT.change}
            </Button>
          </p>
        ))}

      {isDrafting && (
        <div className="flex flex-col gap-1">
          <div
            role="progressbar"
            aria-label={DRAFT_TEXT.progressLabel}
            className="relative h-1 w-full overflow-hidden rounded-full bg-m3-surface-container-highest"
          >
            {/* Linear progress khong xac dinh (UI_GUIDE muc 1h) - cung mau features/resumes/LinearProgress. */}
            <div className="absolute inset-y-0 left-0 w-1/3 rounded-full bg-m3-primary animate-m3-linear-progress motion-reduce:animate-none" />
          </div>
          <p className="text-sm text-m3-on-surface-variant">{DRAFT_TEXT.drafting}</p>
        </div>
      )}

      {options && showChoices && (
        <div className="flex sm:justify-end">
          <Button type="button" className="min-h-10 w-full sm:w-auto" onClick={() => void generate()} disabled={!canGenerate}>
            {DRAFT_TEXT.generate}
          </Button>
        </div>
      )}
    </section>
  )
}

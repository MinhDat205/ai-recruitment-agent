import { AlertCircle, Check, Sparkles, X } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { LinearProgress } from '../resumes/LinearProgress'
import { DESCRIPTION_MAX, JOB_DRAFT_TEXT, PART_LABELS } from './jobDraftLabels'
import { ALL_PARTS, type JobDraftPart } from './types'
import type { JobAiDraftState, PartState } from './useJobAiDraft'

const PANEL_ID = 'job-ai-draft-panel'
const PANEL_TITLE_ID = 'job-ai-draft-panel-title'
const DESCRIPTION_ID = 'job-ai-draft-description'
const SUMMARY_LENGTH = 60

// UI.md 4a/4b/4c/4c-2/4i - khoi "Tao tin bang AI" phia tren Card wizard. Khong hop thoai, khong lop phu: HR van go duoc
// vao bieu mau trong luc cho (UI_GUIDE muc 5). Moi nut sinh khoa khi dang co request J1 (4c).
export function AiDraftInputPanel({ draft }: { draft: JobAiDraftState }) {
  const openButtonRef = useRef<HTMLButtonElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const previousMode = useRef(draft.panelMode)

  // Mo khoi -> focus o mo ta; dong -> focus ve nut mo (UI.md muc 9).
  useEffect(() => {
    if (previousMode.current !== draft.panelMode) {
      if (draft.panelMode === 'input') {
        textareaRef.current?.focus()
      } else if (draft.panelMode === 'closed') {
        openButtonRef.current?.focus()
      }
      previousMode.current = draft.panelMode
    }
  }, [draft.panelMode])

  const length = draft.description.length
  const tooLong = length > DESCRIPTION_MAX
  const canGenerate = !draft.isGenerating && draft.description.trim().length > 0 && !tooLong

  if (draft.panelMode === 'closed') {
    return (
      <section
        aria-labelledby={PANEL_TITLE_ID}
        className="mx-auto mb-4 flex max-w-2xl flex-col gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-4 sm:flex-row sm:items-end sm:justify-between"
      >
        <div className="flex min-w-0 flex-col gap-1">
          <h2 id={PANEL_TITLE_ID} className="flex items-center gap-1.5 text-m3-title-md text-m3-on-surface">
            <Sparkles className="h-4 w-4 shrink-0" aria-hidden="true" />
            {JOB_DRAFT_TEXT.panelTitle}
          </h2>
          <p className="text-sm break-words text-m3-on-surface-variant">{JOB_DRAFT_TEXT.intro}</p>
        </div>
        <Button
          ref={openButtonRef}
          type="button"
          className="min-h-12 w-full shrink-0 sm:min-h-10 sm:w-auto"
          aria-expanded={false}
          aria-controls={PANEL_ID}
          onClick={() => draft.setPanelMode('input')}
        >
          {JOB_DRAFT_TEXT.openButton}
        </Button>
      </section>
    )
  }

  return (
    <section
      id={PANEL_ID}
      aria-labelledby={PANEL_TITLE_ID}
      className="mx-auto mb-4 flex max-w-2xl flex-col gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface p-4"
    >
      <div className="flex items-center justify-between gap-2">
        <h2 id={PANEL_TITLE_ID} className="flex items-center gap-1.5 text-m3-title-md text-m3-on-surface">
          <Sparkles className="h-4 w-4 shrink-0" aria-hidden="true" />
          {JOB_DRAFT_TEXT.panelTitle}
        </h2>
        {/* [X] chi khi CHUA tao lan nao (4b). */}
        {draft.panelMode === 'input' && !draft.aiMode && (
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="min-h-10 min-w-10"
            aria-label={JOB_DRAFT_TEXT.closeLabel}
            onClick={() => draft.setPanelMode('closed')}
          >
            <X aria-hidden="true" />
          </Button>
        )}
      </div>

      {draft.panelMode === 'input' ? (
        <div className="flex flex-col gap-1.5">
          <Label htmlFor={DESCRIPTION_ID}>{JOB_DRAFT_TEXT.descriptionLabel}</Label>
          <Textarea
            id={DESCRIPTION_ID}
            ref={textareaRef}
            rows={5}
            value={draft.description}
            disabled={draft.isGenerating}
            placeholder={JOB_DRAFT_TEXT.descriptionPlaceholder}
            aria-describedby={`${DESCRIPTION_ID}-helper ${DESCRIPTION_ID}-counter`}
            aria-invalid={tooLong || undefined}
            className="min-h-24 break-words"
            onChange={(event) => draft.setDescription(event.target.value)}
          />
          <p id={`${DESCRIPTION_ID}-helper`} className="text-sm break-words text-m3-on-surface-variant">
            {JOB_DRAFT_TEXT.descriptionHelper}
          </p>
          <p
            id={`${DESCRIPTION_ID}-counter`}
            className={`self-end text-sm ${tooLong ? 'text-m3-error' : 'text-m3-on-surface-variant'}`}
          >
            {JOB_DRAFT_TEXT.counter(length)}
          </p>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-m3-on-surface-variant">{JOB_DRAFT_TEXT.quotaNote}</p>
            <Button
              type="button"
              className="min-h-12 w-full sm:min-h-10 sm:w-auto"
              disabled={!canGenerate}
              onClick={() => void draft.generate(ALL_PARTS)}
            >
              {JOB_DRAFT_TEXT.generate}
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          <p className="text-sm break-words text-m3-on-surface">
            {JOB_DRAFT_TEXT.summary(draft.description.trim().slice(0, SUMMARY_LENGTH))}
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button
              type="button"
              variant="outline"
              className="min-h-12 w-full sm:min-h-10 sm:w-auto"
              disabled={draft.isGenerating}
              onClick={() => draft.setPanelMode('input')}
            >
              {JOB_DRAFT_TEXT.editDescription}
            </Button>
            <Button
              type="button"
              variant="outline"
              className="min-h-12 w-full sm:min-h-10 sm:w-auto"
              disabled={draft.isGenerating}
              onClick={() => void draft.generate(ALL_PARTS)}
            >
              {JOB_DRAFT_TEXT.regenerateAll}
            </Button>
          </div>
        </div>
      )}

      {draft.aiMode && (
        <ul aria-live="polite" className="flex flex-col gap-2">
          {ALL_PARTS.map((part) => (
            <PartStatusRow
              key={part}
              part={part}
              state={draft.parts[part]}
              disabled={draft.isGenerating}
              onRetry={(parts) => void draft.generate(parts)}
            />
          ))}
        </ul>
      )}
    </section>
  )
}

function PartStatusRow({
  part,
  state,
  disabled,
  onRetry,
}: {
  part: JobDraftPart
  state: PartState
  disabled: boolean
  onRetry: (parts: JobDraftPart[]) => void
}) {
  const label = PART_LABELS[part]
  if (!state.running && !state.error && !state.hadSuccess) {
    return null
  }
  return (
    <li aria-busy={state.running || undefined} className="flex flex-col gap-1 sm:flex-row sm:items-center sm:gap-3">
      <span className="w-40 shrink-0 text-sm font-medium text-m3-on-surface">{label}</span>
      <div className="min-w-0 flex-1">
        {state.running ? (
          <div className="flex items-center gap-3">
            <div className="flex-1">
              <LinearProgress label={JOB_DRAFT_TEXT.progressLabel(label)} />
            </div>
            <span className="text-sm text-m3-on-surface-variant">{JOB_DRAFT_TEXT.running}</span>
          </div>
        ) : state.error ? (
          <PartError state={state} disabled={disabled} onRetry={onRetry} />
        ) : (
          <p className="flex items-center gap-1.5 text-sm text-m3-on-surface">
            <Check className="h-4 w-4 shrink-0" aria-hidden="true" />
            {JOB_DRAFT_TEXT.done}
          </p>
        )}
      </div>
    </li>
  )
}

// Cau loi cua mot phan (4i): message cua o FAILED hoac cua ca request; "Chua tao lai duoc..." khi dang hien goi y cu
// (4f); "Thu lai" khi co retryParts; lien ket tao ho so cong ty khi 404 COMPANY_NOT_FOUND.
function PartError({
  state,
  disabled,
  onRetry,
}: {
  state: PartState
  disabled: boolean
  onRetry: (parts: JobDraftPart[]) => void
}) {
  const error = state.error
  if (!error) {
    return null
  }
  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <div role="alert" className="flex min-w-0 flex-col gap-0.5 text-sm break-words text-m3-on-surface">
        <p className="flex items-start gap-1.5">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          {error.message}
        </p>
        {state.hadSuccess && <p className="ml-5.5">{JOB_DRAFT_TEXT.staleNotice}</p>}
        {error.companyMissing && (
          <Link to="/hr/company" className="ml-5.5 text-m3-on-primary-container underline">
            {JOB_DRAFT_TEXT.createCompany}
          </Link>
        )}
      </div>
      {error.retryParts && (
        <Button
          type="button"
          variant="outline"
          className="min-h-12 w-full shrink-0 sm:min-h-10 sm:w-auto"
          disabled={disabled}
          onClick={() => error.retryParts && onRetry(error.retryParts)}
        >
          {JOB_DRAFT_TEXT.retry}
        </Button>
      )}
    </div>
  )
}

// Dau buoc 1/3/4 cua che do AI (UI.md muc 6): dang tao -> skeleton 3 dong (chua co goi y) hoac thanh tien trinh (giu
// goi y cu); loi -> cau loi + "Thu lai" thay khoi goi y (hoac tren goi y cu).
export function PartStepStatus({
  part,
  state,
  disabled,
  onRetry,
}: {
  part: JobDraftPart
  state: PartState
  disabled: boolean
  onRetry: (parts: JobDraftPart[]) => void
}) {
  if (state.running) {
    if (state.hadSuccess) {
      return <LinearProgress label={JOB_DRAFT_TEXT.progressLabel(PART_LABELS[part])} />
    }
    return (
      <div className="flex flex-col gap-2" aria-hidden="true">
        {[0, 1, 2].map((line) => (
          <div
            key={line}
            className="h-5 w-full animate-pulse rounded-m3-xs bg-m3-surface-container-highest motion-reduce:animate-none"
          />
        ))}
      </div>
    )
  }
  if (state.error) {
    return <PartError state={state} disabled={disabled} onRetry={onRetry} />
  }
  return null
}

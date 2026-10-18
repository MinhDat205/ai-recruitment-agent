import { useMutation } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { AlertCircle, Search } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'
import { extractErrorMessage } from '../../lib/httpError'
import { JobCardSkeleton } from '../jobs/JobCardSkeleton'
import { LinearProgress } from '../resumes/LinearProgress'
import { searchJobsByDescription } from './api'
import { InterpretationPanel } from './InterpretationPanel'
import { JOB_SEARCH_MAX_LENGTH, JOB_SEARCH_TEXT, nlCounter } from './jobSearchLabels'
import { NaturalLanguageResults } from './NaturalLanguageResults'
import { normalizeQuery, validateQuery } from './normalizeQuery'
import type { JobSearchCondition, JobSearchInterpretation, JobSearchResponse } from './types'

// sentQuery = chuoi DUNG nhu luc gui (chua chuan hoa - backend tu chuan hoa); submissionId = ma luot gui.
type SearchState =
  | { status: 'idle' }
  | { status: 'pending'; submissionId: number; sentQuery: string }
  | { status: 'done'; submissionId: number; sentQuery: string; response: JobSearchResponse }
  | { status: 'error'; submissionId: number; sentQuery: string; message: string }

const IDLE: SearchState = { status: 'idle' }

interface NaturalLanguageSearchProps {
  // Cau dang go do JobBoard giu: chuyen sang tu khoa roi quay lai thi o van con cau cu (UI.md muc 6), ket qua thi khong.
  query: string
  onQueryChange: (query: string) => void
  onEditConditions: (interpretation: JobSearchInterpretation, removed?: JobSearchCondition) => void
}

// FR-U13 UI.md muc 4a, 4c, 5a, 6, 9 - toan bo che do tim bang mo ta. Khung form la nua duoi cua thanh tim kiem; nua
// tren (bo chon che do) do JobBoard ve. Nam quy tac (UI.md 5a): (1) moi lan gui ghi lai cau da gui, "Thu lai" gui DUNG
// cau do; (2) dang cho thi khong gui them (nut aria-disabled, Enter bi bo qua); (3) ket qua ve sau unmount hoac sau mot
// luot gui moi hon -> bo (so ma luot gui); (4) gui moi -> an ket qua cu ngay; (5) sua o khong xoa ket qua.
export function NaturalLanguageSearch({ query, onQueryChange, onEditConditions }: NaturalLanguageSearchProps) {
  const [state, setState] = useState<SearchState>(IDLE)
  // Loi hien duoi o: loi kiem phia client hoac 400 kiem dau vao cua backend.
  const [fieldError, setFieldError] = useState<string | null>(null)
  const latestSubmissionRef = useRef(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const mutation = useMutation({ mutationFn: searchJobsByDescription })

  const baseId = useId()
  const inputId = `${baseId}-input`
  const errorId = `${baseId}-error`
  const hintId = `${baseId}-hint`
  const counterId = `${baseId}-counter`

  // Quy tac 3 - unmount: tang ma luot de moi ket qua dang bay ve deu thanh "cu" va bi bo.
  useEffect(() => {
    const submissions = latestSubmissionRef
    return () => {
      submissions.current += 1
    }
  }, [])

  const pending = state.status === 'pending'
  const normalizedLength = normalizeQuery(query).length

  async function send(sentQuery: string) {
    latestSubmissionRef.current += 1
    const submissionId = latestSubmissionRef.current
    setFieldError(null)
    setState({ status: 'pending', submissionId, sentQuery })
    try {
      const response = await mutation.mutateAsync({ query: sentQuery })
      if (latestSubmissionRef.current !== submissionId) {
        return
      }
      setState({ status: 'done', submissionId, sentQuery, response })
    } catch (err) {
      if (latestSubmissionRef.current !== submissionId) {
        return
      }
      // Cau message cua backend neu body la {error, message}; khong co -> cau du phong. Khong hien ma loi tho.
      const message = extractErrorMessage(err, JOB_SEARCH_TEXT.nlFailedFallback)
      if (isAxiosError(err) && err.response?.status === 400) {
        // 400 kiem dau vao: hien duoi o, khong o khoi loi (UI.md muc 6).
        setState(IDLE)
        setFieldError(message)
        inputRef.current?.focus()
        return
      }
      setState({ status: 'error', submissionId, sentQuery, message })
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) {
      return
    }
    // Kiem 2-300 TRUOC khi goi API; nut KHONG khoa vi ly do nay (UI.md muc 6).
    const validation = validateQuery(query)
    if (validation !== null) {
      setFieldError(JOB_SEARCH_TEXT[validation])
      inputRef.current?.focus()
      return
    }
    void send(query)
  }

  function handleRetry() {
    if (state.status !== 'error') {
      return
    }
    void send(state.sentQuery)
  }

  function handleQueryChange(next: string) {
    onQueryChange(next)
    setFieldError(null)
  }

  const describedBy = [fieldError ? errorId : null, hintId, counterId].filter(Boolean).join(' ')

  return (
    <div className="flex flex-col gap-4">
      <form
        onSubmit={handleSubmit}
        noValidate
        className="flex flex-col gap-2 rounded-b-m3-sm border border-t-0 border-m3-outline-variant bg-m3-surface px-4 pt-3 pb-4"
      >
        <Label htmlFor={inputId} className="text-m3-label-lg text-m3-on-surface">
          {JOB_SEARCH_TEXT.nlInputLabel}
        </Label>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <div className="flex h-12 flex-1 items-center gap-2 rounded-m3-xs border border-m3-outline bg-m3-surface px-3 sm:h-10">
            <Search className="h-4 w-4 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
            <input
              ref={inputRef}
              id={inputId}
              type="text"
              value={query}
              onChange={(event) => handleQueryChange(event.target.value)}
              placeholder={JOB_SEARCH_TEXT.nlPlaceholder}
              aria-invalid={fieldError ? true : undefined}
              aria-describedby={describedBy}
              className="w-full bg-transparent text-m3-body-md text-m3-on-surface outline-none placeholder:text-m3-on-surface-variant"
            />
          </div>
          <button
            type="submit"
            aria-disabled={pending || undefined}
            className="flex h-12 w-full shrink-0 items-center justify-center rounded-m3-button bg-m3-primary px-4 text-m3-label-lg text-m3-on-primary aria-disabled:cursor-not-allowed aria-disabled:opacity-38 sm:h-10 sm:w-auto"
          >
            {pending ? JOB_SEARCH_TEXT.nlSubmitting : JOB_SEARCH_TEXT.nlSubmit}
          </button>
        </div>

        {fieldError && (
          <p id={errorId} role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-error">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
            {fieldError}
          </p>
        )}

        <div className="flex items-start justify-between gap-3 text-m3-body-sm text-m3-on-surface-variant">
          <p id={hintId}>{JOB_SEARCH_TEXT.nlHint}</p>
          <p
            id={counterId}
            className={cn('shrink-0 tabular-nums', normalizedLength > JOB_SEARCH_MAX_LENGTH && 'text-m3-error')}
          >
            {nlCounter(normalizedLength)}
          </p>
        </div>
        <p className="text-m3-body-sm text-m3-on-surface-variant">{JOB_SEARCH_TEXT.nlQuotaNote}</p>
      </form>

      {state.status === 'idle' && <p className="text-sm text-m3-on-surface">{JOB_SEARCH_TEXT.nlIntro}</p>}

      {state.status === 'pending' && (
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <LinearProgress label={JOB_SEARCH_TEXT.nlProgress} />
            <p className="text-sm text-m3-on-surface">{JOB_SEARCH_TEXT.nlProgress}</p>
          </div>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            {Array.from({ length: 4 }).map((_, index) => (
              <JobCardSkeleton key={index} />
            ))}
          </div>
        </div>
      )}

      {state.status === 'error' && (
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
            {state.message}
          </p>
          <Button type="button" variant="outline" className="min-h-12 w-full sm:min-h-10 sm:w-auto" onClick={handleRetry}>
            {JOB_SEARCH_TEXT.retry}
          </Button>
        </div>
      )}

      {state.status === 'done' && (
        <>
          <InterpretationPanel
            interpretation={state.response.interpretation}
            ranking={state.response.ranking}
            onEditConditions={(removed) => onEditConditions(state.response.interpretation, removed)}
          />
          <NaturalLanguageResults
            key={state.submissionId}
            interpretation={state.response.interpretation}
            items={state.response.items}
            ranking={state.response.ranking}
            onEditConditions={() => onEditConditions(state.response.interpretation)}
          />
        </>
      )}
    </div>
  )
}

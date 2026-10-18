import { AlertCircle, Sparkles } from 'lucide-react'
import { useId, useState } from 'react'
import { useFormContext, useWatch } from 'react-hook-form'
import { Button } from '@/components/ui/button'
import { extractErrorMessage } from '../../lib/httpError'
import { LinearProgress } from '../resumes/LinearProgress'
import { rephraseDescription } from './api'
import { CV_BUILDER_LIMITS, CV_BUILDER_TEXT } from './cvBuilderLabels'
import { nfcLength, normalizeMultiLine, type CvBuilderFormValues } from './cvBuilderSchema'
import type { CvRephraseKind } from './types'

// Khoa "chi mot request goi y cung luc tren ca trang" (UI.md muc 5a quy tac 3) - CvBuilderFormView giu owner.
export interface RephraseLock {
  owner: string | null
  acquire: (owner: string) => void
  release: (owner: string) => void
}

type ValidationKey = 'rephraseEmpty' | 'rephraseTooShort' | 'rephraseTooLong'

// sentText = doan DA chuan hoa R-F3 dung nhu luc gui - ket qua/loi chi co nghia voi dung noi dung nay.
type RephraseState =
  | { status: 'idle'; validation: ValidationKey | null }
  | { status: 'pending'; sentText: string }
  | { status: 'done'; sentText: string; suggestion: string }
  | { status: 'error'; sentText: string; message: string }

const IDLE: RephraseState = { status: 'idle', validation: null }

interface RephraseAssistProps {
  name: `experience.${number}.description` | `projects.${number}.description`
  kind: CvRephraseKind
  // id on dinh cua muc (useFieldArray fields[].id) - chu cua khoa trang.
  ownerKey: string
  lock: RephraseLock
}

// R-A2 - kiem do dai 20-800 phia client TRUOC khi goi (cung thu tu va cau voi backend), dem sau NFC (L56). null = hop le.
function validate(text: string | null): ValidationKey | null {
  if (text === null) {
    return 'rephraseEmpty'
  }
  const length = nfcLength(text)
  if (length < CV_BUILDER_LIMITS.rephraseMin) {
    return 'rephraseTooShort'
  }
  if (length > CV_BUILDER_LIMITS.rephraseMax) {
    return 'rephraseTooLong'
  }
  return null
}

// So noi dung bo qua khac biet dung san / to hop cua chu co dau - chi de so, khong doi du lieu gui di.
function sameText(a: string, b: string | null): boolean {
  return b !== null && a.normalize('NFC') === b.normalize('NFC')
}

// FR-U12 UI.md muc 4a, 5a, 6, 9 - goi y dien dat cho MOT o mo ta (R-A1). Chi goi khi bam nut (khong tu goi khi go hay
// roi o); chi thay noi dung o khi bam "Dùng gợi ý" (R-A5). Ket qua gan voi noi dung luc gui: noi dung o da khac thi
// khong hien ket qua (quy tac 1); muc bi xoa thi component unmount, ket qua ve bi bo cung state (quy tac 2).
export function RephraseAssist({ name, kind, ownerKey, lock }: RephraseAssistProps) {
  const { control, setValue, setFocus } = useFormContext<CvBuilderFormValues>()
  const value = useWatch({ control, name })
  const [state, setState] = useState<RephraseState>(IDLE)

  const baseId = useId()
  const messageId = `${baseId}-message`

  const currentText = normalizeMultiLine(value ?? '')
  const pending = state.status === 'pending'
  const busyOther = lock.owner !== null && lock.owner !== ownerKey
  // Ket qua/loi thuoc dung noi dung dang co trong o.
  const fresh = state.status !== 'idle' && state.sentText === currentText

  async function send(text: string) {
    lock.acquire(ownerKey)
    setState({ status: 'pending', sentText: text })
    try {
      const response = await rephraseDescription({ kind, text })
      setState((prev) =>
        prev.status === 'pending' && prev.sentText === text
          ? { status: 'done', sentText: text, suggestion: response.suggestion }
          : prev,
      )
    } catch (err) {
      // Cau message cua backend neu co {error, message} (400, 429, 502, 503, 504); khong co -> cau du phong.
      const message = extractErrorMessage(err, CV_BUILDER_TEXT.rephraseFailedFallback)
      setState((prev) =>
        prev.status === 'pending' && prev.sentText === text ? { status: 'error', sentText: text, message } : prev,
      )
    } finally {
      lock.release(ownerKey)
    }
  }

  function handleRequest() {
    // Nut KHONG khoa vi loi kiem (UI.md muc 7) - chi khoa khi dang cho; o khac dang cho thi aria-disabled.
    if (pending || busyOther) {
      return
    }
    const validation = validate(currentText)
    if (validation !== null || currentText === null) {
      setState({ status: 'idle', validation })
      setFocus(name)
      return
    }
    void send(currentText)
  }

  function handleRetry() {
    if (state.status !== 'error' || pending || busyOther) {
      return
    }
    void send(state.sentText)
  }

  function handleUse() {
    if (state.status !== 'done') {
      return
    }
    setValue(name, state.suggestion, { shouldDirty: true })
    setState(IDLE)
  }

  const validationMessage = state.status === 'idle' && state.validation ? CV_BUILDER_TEXT[state.validation] : null
  const showSuggestion = state.status === 'done' && fresh
  const same = state.status === 'done' && sameText(state.suggestion, currentText)

  return (
    <div className="flex flex-col gap-2">
      <div className="flex sm:justify-end">
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="min-h-10 w-full sm:w-auto"
          disabled={pending}
          aria-disabled={busyOther || undefined}
          aria-describedby={validationMessage || busyOther ? messageId : undefined}
          onClick={handleRequest}
        >
          <Sparkles aria-hidden="true" />
          {CV_BUILDER_TEXT.rephraseButton}
        </Button>
      </div>

      {validationMessage && (
        <p id={messageId} role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-error">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          {validationMessage}
        </p>
      )}
      {!validationMessage && busyOther && (
        <p id={messageId} className="text-sm text-m3-on-surface-variant">
          {CV_BUILDER_TEXT.rephraseBusyOther}
        </p>
      )}

      {pending && (
        <div className="flex flex-col gap-1">
          <LinearProgress label={CV_BUILDER_TEXT.rephraseProgress} />
          <p className="text-sm text-m3-on-surface-variant">{CV_BUILDER_TEXT.rephraseHint}</p>
        </div>
      )}

      {state.status === 'error' && (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <p role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
            {state.message}
          </p>
          {/* "Thu lai" gui dung noi dung luc gui; noi dung o da doi thi an (UI.md muc 6). */}
          {fresh && (
            <Button
              type="button"
              variant="outline"
              className="min-h-10 w-full sm:w-auto"
              aria-disabled={busyOther || undefined}
              onClick={handleRetry}
            >
              {CV_BUILDER_TEXT.retry}
            </Button>
          )}
        </div>
      )}

      {/* Khoi AI (UI_GUIDE muc 4): vien bat buoc, chu CHI text-m3-on-surface tren nen surface-container-high. */}
      <div aria-live="polite">
        {showSuggestion && (
          <div className="flex flex-col gap-2 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container-high p-3">
            <p className="flex items-center gap-1.5 text-sm font-medium text-m3-on-surface">
              <Sparkles className="h-4 w-4 shrink-0" aria-hidden="true" />
              {CV_BUILDER_TEXT.aiBadge}
            </p>
            {same ? (
              <p className="text-sm text-m3-on-surface">{CV_BUILDER_TEXT.rephraseSame}</p>
            ) : (
              <>
                <p className="text-sm break-words whitespace-pre-wrap text-m3-on-surface">{state.suggestion}</p>
                <p className="text-sm text-m3-on-surface">{CV_BUILDER_TEXT.rephraseCaution}</p>
              </>
            )}
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button type="button" variant="outline" className="min-h-10 w-full sm:w-auto" onClick={() => setState(IDLE)}>
                {CV_BUILDER_TEXT.rephraseDismiss}
              </Button>
              {!same && (
                <Button type="button" className="min-h-10 w-full sm:w-auto" onClick={handleUse}>
                  {CV_BUILDER_TEXT.rephraseUse}
                </Button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

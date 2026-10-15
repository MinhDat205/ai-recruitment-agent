import { useId, useRef, useState, type FormEvent } from 'react'
import { AlertCircle, Info } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { LinearProgress } from '../resumes/LinearProgress'
import { CvQaTurnItem } from './CvQaTurnItem'
import { CV_QA_TEXT, MAX_QUESTION_LENGTH, cvQaCounter } from './cvQaLabels'
import type { CvQaSide } from './types'
import type { CvQaSession } from './useCvQaSession'

type ValidationError = 'emptyQuestion' | 'questionTooLong'

// O hoi + loi kiem phia client, GAN voi khoa phien: khoa doi -> o hoi trong (UI.md muc 6 "Doi sang don/CV khac").
interface QuestionDraft {
  key: string
  text: string
  validation: ValidationError | null
}

const INTRO_TEXT: Record<CvQaSide, string> = {
  hr: CV_QA_TEXT.introHr,
  candidate: CV_QA_TEXT.introCandidate,
}

const PLACEHOLDER_TEXT: Record<CvQaSide, string> = {
  hr: CV_QA_TEXT.placeholderHr,
  candidate: CV_QA_TEXT.placeholderCandidate,
}

// FR-C08 - khoi hoi dap CV dung chung hai phia (UI.md muc 4a, 4b, 5d, 6, 9). Luot, loi va trang thai cho nam trong
// session (useCvQaSession o cap trang) - component nay chi giu o hoi. resumeId chi phia ung vien (lien ket U05).
export function CvQaPanel({ side, session, resumeId }: { side: CvQaSide; session: CvQaSession; resumeId?: string }) {
  const [draft, setDraft] = useState<QuestionDraft>(() => ({ key: session.key, text: '', validation: null }))
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const baseId = useId()
  const questionId = `${baseId}-question`
  const counterId = `${baseId}-counter`
  const validationId = `${baseId}-validation`

  // Khoa doi -> xoa o hoi ngay trong lan render nay (cung mau voi useCvQaSession).
  let current = draft
  if (draft.key !== session.key) {
    current = { key: session.key, text: '', validation: null }
    setDraft(current)
  }

  const length = current.text.length
  const tooLong = length > MAX_QUESTION_LENGTH
  const pending = session.pending

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) {
      return
    }
    // Kiem phia client truoc khi goi API (UI.md muc 7): sai thi KHONG goi, focus vao o; nut khong khoa vi ly do nay.
    const question = current.text.trim()
    const validation: ValidationError | null =
      question.length === 0 ? 'emptyQuestion' : question.length > MAX_QUESTION_LENGTH ? 'questionTooLong' : null
    setDraft({ ...current, validation })
    if (validation !== null) {
      textareaRef.current?.focus()
      return
    }
    session.ask(question)
  }

  const describedBy = current.validation ? `${counterId} ${validationId}` : counterId

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <p className="text-sm break-words text-m3-on-surface">{INTRO_TEXT[side]}</p>
        <p className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface-variant">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-m3-on-surface-variant" aria-hidden="true" />
          {CV_QA_TEXT.notSavedNotice}
        </p>
      </div>

      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-2">
        <Label htmlFor={questionId} className="text-m3-on-surface">
          {CV_QA_TEXT.questionLabel}
        </Label>
        {/* O hoi VAN go duoc khi dang cho (UI.md muc 6). */}
        <Textarea
          id={questionId}
          ref={textareaRef}
          value={current.text}
          onChange={(event) => setDraft({ ...current, text: event.target.value, validation: null })}
          placeholder={PLACEHOLDER_TEXT[side]}
          aria-describedby={describedBy}
          aria-invalid={tooLong || current.validation !== null || undefined}
          className="min-h-20 break-words"
        />
        <span
          id={counterId}
          className={`self-end text-sm ${tooLong ? 'text-m3-error' : 'text-m3-on-surface-variant'}`}
        >
          {cvQaCounter(length)}
        </span>
        {current.validation && (
          <p id={validationId} role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
            {CV_QA_TEXT[current.validation]}
          </p>
        )}
        <div className="flex sm:justify-end">
          <Button type="submit" className="min-h-10 w-full sm:w-auto" disabled={pending}>
            {pending ? CV_QA_TEXT.asking : CV_QA_TEXT.askButton}
          </Button>
        </div>
      </form>

      {pending && (
        <div className="flex flex-col gap-1">
          <LinearProgress label={CV_QA_TEXT.progressLabel} />
          <p className="text-sm text-m3-on-surface-variant">{CV_QA_TEXT.progressHint}</p>
        </div>
      )}

      {/* 4b-4: loi KHONG them luot; focus khong tu nhay (UI.md muc 9). Khuon MessageDraftPanel.tsx:170-177. */}
      {session.error && (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <p role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
            {session.error}
          </p>
          {session.retryQuestion !== null && (
            <Button type="button" variant="outline" className="min-h-10 w-full sm:w-auto" onClick={session.retry}>
              {CV_QA_TEXT.retry}
            </Button>
          )}
        </div>
      )}

      <div aria-live="polite" aria-busy={pending} className="flex flex-col gap-6">
        {session.turns.length === 0 ? (
          !pending && <p className="text-sm text-m3-on-surface-variant">{CV_QA_TEXT.emptyTurns}</p>
        ) : (
          session.turns.map((turn, index) => (
            <div key={turn.id} className={index > 0 ? 'border-t border-m3-outline-variant pt-6' : undefined}>
              <CvQaTurnItem turn={turn} side={side} resumeId={resumeId} />
            </div>
          ))
        )}
      </div>
    </div>
  )
}

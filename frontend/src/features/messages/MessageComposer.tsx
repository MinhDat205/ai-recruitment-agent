import { useId, useRef, useState, type ChangeEvent, type KeyboardEvent } from 'react'
import { isAxiosError } from 'axios'
import { AlertCircle, Paperclip, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { extractErrorMessage } from '../../lib/httpError'
import { DRAFT_TEXT } from '../messageDraft/draftLabels'
import { MessageDraftPanel } from '../messageDraft/MessageDraftPanel'
import { ReplaceDraftDialog } from '../messageDraft/ReplaceDraftDialog'
import { INITIAL_DRAFT_SELECTION, type DraftSelection } from '../messageDraft/types'
import { AttachmentChip } from './AttachmentChip'
import { ATTACHMENT_ACCEPT, MAX_BODY_LENGTH, validateAttachment } from './messageRules'
import { useSendMessageMutation } from './queries'
import type { MessageSide } from './types'

const SEND_FAILED_FALLBACK = 'Gửi tin nhắn thất bại, vui lòng thử lại.'

function isReadOnlyConflict(error: unknown): boolean {
  if (!isAxiosError(error) || error.response?.status !== 409) {
    return false
  }
  const data = error.response.data as { error?: unknown } | undefined
  return data?.error === 'CONVERSATION_READ_ONLY'
}

// Khung soan (UI.md muc 4a, 6, 7, 9). Duoi vung tin, KHONG fixed/sticky. Noi dung gui NGUYEN VAN (R-M2) - khong
// trim/escape o day, ke ca khi chu den tu ban nhap AI (FR-C07 Q12: khong chan "Gui" khi con cho trong [...]).
// FR-C07 them nut "Soan bang AI" + khoi MessageDraftPanel phia tren o nhap (FR-C07 UI.md muc 4, 5b). Ban nhap chi vao
// o nhap khi bam "Dung ban nhap" (hoi truoc neu o nhap co chu - muc 4f); khong tu gui, khong gan co "do AI soan"
// (R-D2, R-D3). submit, kiem tep, bo dem, phim tat, cach goi M2 KHONG doi.
// Loi gui (400/429/mang): cau backend tai cho, chu va tep con nguyen. 409 CONVERSATION_READ_ONLY: bao len
// MessagesTab (onReadOnlyConflict) de lam moi M1 + E1 va hien cau loi o cap tab (khung soan bien mat).
export function MessageComposer({
  side,
  applicationId,
  onSent,
  onReadOnlyConflict,
}: {
  side: MessageSide
  applicationId: string
  onSent: () => void
  onReadOnlyConflict: (message: string) => void
}) {
  const [text, setText] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const sendMutation = useSendMessageMutation(side, applicationId)

  // FR-C07 - khoi soan nhap. Lua chon (tinh huong, giong van, muc dich) giu o day de con nguyen khi dong/mo lai khoi
  // trong cung lan xem tab (UI.md muc 4b); ban nhap thi nam trong khoi va mat khi dong khoi (R-D1).
  const [draftOpen, setDraftOpen] = useState(false)
  const [draftSelection, setDraftSelection] = useState<DraftSelection>(INITIAL_DRAFT_SELECTION)
  const [pendingDraft, setPendingDraft] = useState<string | null>(null)
  const [draftHintShown, setDraftHintShown] = useState(false)
  const draftButtonRef = useRef<HTMLButtonElement>(null)
  // Gui thanh cong (C06): dong khoi soan nhap va an dong nhac (UI.md muc 6) - suy tu ket qua M2 moi, khong sua submit.
  const [seenSendResult, setSeenSendResult] = useState(sendMutation.data)
  if (sendMutation.data !== seenSendResult) {
    setSeenSendResult(sendMutation.data)
    setDraftOpen(false)
    setDraftHintShown(false)
  }

  const baseId = useId()
  const textareaId = `${baseId}-text`
  const counterId = `${baseId}-counter`
  const errorId = `${baseId}-error`
  const fileInputId = `${baseId}-file`
  const draftPanelId = `${baseId}-ai-draft`

  const isSending = sendMutation.isPending
  const tooLong = text.length > MAX_BODY_LENGTH
  // Nut "Gui" khoa khi: chu rong/chi khoang trang VA khong co tep; chu qua 4000; dang gui (UI.md muc 6).
  const canSubmit = !isSending && !tooLong && (text.trim().length > 0 || file !== null)

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0] ?? null
    // Xoa gia tri de chon lai CUNG tep sau khi bi loi van kich hoat onChange.
    event.target.value = ''
    if (!selected) {
      return
    }
    const validationError = validateAttachment(selected)
    if (validationError) {
      // Tep KHONG duoc chon; chu dang nhap con nguyen.
      setError(validationError)
      return
    }
    setError(null)
    setFile(selected)
  }

  async function submit() {
    if (!canSubmit) {
      return
    }
    setError(null)
    try {
      await sendMutation.mutateAsync({ body: text, file })
      setText('')
      setFile(null)
      onSent()
      textareaRef.current?.focus()
    } catch (err) {
      if (isReadOnlyConflict(err)) {
        onReadOnlyConflict(extractErrorMessage(err, SEND_FAILED_FALLBACK))
        return
      }
      setError(extractErrorMessage(err, SEND_FAILED_FALLBACK))
    }
  }

  function closeDraftPanel() {
    setDraftOpen(false)
    // Dong khoi thi focus ve nut "Soan bang AI" (FR-C07 UI.md muc 9).
    requestAnimationFrame(() => draftButtonRef.current?.focus())
  }

  // FR-C07 UI.md muc 4e - ban nhap vao o nhap (thay toan bo chu), khoi dong, focus ve o nhap voi con tro o dau. Tep da
  // chon khong bi dung.
  function applyDraft(draft: string) {
    setText(draft)
    setPendingDraft(null)
    setDraftOpen(false)
    setDraftHintShown(true)
    requestAnimationFrame(() => {
      const textarea = textareaRef.current
      if (textarea) {
        textarea.focus()
        textarea.setSelectionRange(0, 0)
        textarea.scrollTop = 0
      }
    })
  }

  // Muc 4f (Q4) - o nhap chi co khoang trang coi la rong (khong hoi).
  function handleUseDraft(draft: string) {
    if (text.trim().length === 0) {
      applyDraft(draft)
      return
    }
    setPendingDraft(draft)
  }

  function handleTextChange(value: string) {
    setText(value)
    // Dong nhac 4e mat khi o nhap bi xoa trang.
    if (value.length === 0) {
      setDraftHintShown(false)
    }
  }

  // Enter xuong dong; Ctrl+Enter (va Cmd+Enter) gui (UI.md muc 9).
  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault()
      void submit()
    }
  }

  return (
    <div className="flex flex-col gap-2">
      {draftOpen && (
        <MessageDraftPanel
          side={side}
          applicationId={applicationId}
          panelId={draftPanelId}
          selection={draftSelection}
          onSelectionChange={setDraftSelection}
          onUseDraft={handleUseDraft}
          onClose={closeDraftPanel}
          onReadOnlyConflict={onReadOnlyConflict}
        />
      )}

      <label htmlFor={textareaId} className="sr-only">
        Nhập tin nhắn
      </label>
      <Textarea
        id={textareaId}
        ref={textareaRef}
        value={text}
        onChange={(event) => handleTextChange(event.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="Nhập tin nhắn…"
        // readOnly (khong disabled) khi dang gui: van khoa nhap nhung giu duoc focus de tra focus ve o nhap
        // ngay sau khi gui xong (UI.md muc 6, 9).
        readOnly={isSending}
        aria-describedby={error ? `${counterId} ${errorId}` : counterId}
        aria-invalid={tooLong || undefined}
        className="min-h-20 break-words"
      />

      {draftHintShown && text.length > 0 && (
        <p className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface-variant">
          <Sparkles className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          {DRAFT_TEXT.usedHint}
        </p>
      )}

      <div className="flex flex-col gap-1 sm:flex-row sm:flex-wrap sm:items-center sm:gap-3">
        <input
          id={fileInputId}
          ref={fileInputRef}
          type="file"
          accept={ATTACHMENT_ACCEPT}
          onChange={handleFileChange}
          className="sr-only"
          tabIndex={-1}
          aria-hidden="true"
        />
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="self-start"
          onClick={() => fileInputRef.current?.click()}
          disabled={isSending}
        >
          <Paperclip aria-hidden="true" />
          Đính kèm tệp
        </Button>
        <Button
          ref={draftButtonRef}
          type="button"
          variant="outline"
          size="sm"
          className="self-start"
          aria-expanded={draftOpen}
          aria-controls={draftOpen ? draftPanelId : undefined}
          onClick={() => (draftOpen ? closeDraftPanel() : setDraftOpen(true))}
        >
          <Sparkles aria-hidden="true" />
          {DRAFT_TEXT.openButton}
        </Button>
        <span className="text-sm text-m3-on-surface-variant">PDF, DOCX, PNG, JPEG, WEBP · tối đa 5MB</span>
      </div>

      {file && (
        <div className="max-w-full sm:max-w-sm">
          <AttachmentChip
            fileName={file.name}
            fileSize={file.size}
            onRemove={() => setFile(null)}
            disabled={isSending}
          />
        </div>
      )}

      {error && (
        <p id={errorId} role="alert" className="flex items-start gap-1.5 text-sm break-words text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          {error}
        </p>
      )}

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-end sm:gap-4">
        <span
          id={counterId}
          className={`text-sm ${tooLong ? 'text-m3-error' : 'text-m3-on-surface-variant'}`}
        >
          {text.length}/{MAX_BODY_LENGTH}
        </span>
        <Button type="button" onClick={() => void submit()} disabled={!canSubmit} className="w-full sm:w-auto">
          {isSending ? 'Đang gửi…' : 'Gửi'}
        </Button>
      </div>

      <ReplaceDraftDialog
        open={pendingDraft !== null}
        onKeep={() => setPendingDraft(null)}
        onReplace={() => pendingDraft !== null && applyDraft(pendingDraft)}
      />
    </div>
  )
}

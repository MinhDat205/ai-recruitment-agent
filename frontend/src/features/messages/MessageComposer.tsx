import { useId, useRef, useState, type ChangeEvent, type KeyboardEvent } from 'react'
import { isAxiosError } from 'axios'
import { AlertCircle, Paperclip } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { extractErrorMessage } from '../../lib/httpError'
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

// Khung soan (UI.md muc 4a, 6, 7, 9). Duoi vung tin, KHONG fixed/sticky. Khong co nut "Soan bang AI" hay cho
// trong cho no (R-P6 - thuoc FR-C07). Noi dung gui NGUYEN VAN (R-M2) - khong trim/escape o day.
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

  const baseId = useId()
  const textareaId = `${baseId}-text`
  const counterId = `${baseId}-counter`
  const errorId = `${baseId}-error`
  const fileInputId = `${baseId}-file`

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

  // Enter xuong dong; Ctrl+Enter (va Cmd+Enter) gui (UI.md muc 9).
  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault()
      void submit()
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={textareaId} className="sr-only">
        Nhập tin nhắn
      </label>
      <Textarea
        id={textareaId}
        ref={textareaRef}
        value={text}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="Nhập tin nhắn…"
        // readOnly (khong disabled) khi dang gui: van khoa nhap nhung giu duoc focus de tra focus ve o nhap
        // ngay sau khi gui xong (UI.md muc 6, 9).
        readOnly={isSending}
        aria-describedby={error ? `${counterId} ${errorId}` : counterId}
        aria-invalid={tooLong || undefined}
        className="min-h-20 break-words"
      />

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
    </div>
  )
}

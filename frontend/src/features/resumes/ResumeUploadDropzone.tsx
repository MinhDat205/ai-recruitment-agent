import { isAxiosError } from 'axios'
import { UploadCloud } from 'lucide-react'
import { useRef, useState, type ChangeEvent, type DragEvent, type KeyboardEvent } from 'react'
import { useUploadResumeMutation } from './queries'

const MAX_SIZE_BYTES = 10 * 1024 * 1024
const ACCEPTED_EXTENSIONS = ['.pdf', '.docx']

// Backend tra loi qua ErrorResponse { error, message } (xem GlobalExceptionHandler) - lay dung
// message do thay vi chuoi cung, chi fallback khi response khong dung dang nay (vd loi mang).
function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

// Chi kiem tra nhe o client de phan hoi UX nhanh - validate that (magic bytes, size) luon o backend.
function validateClientSide(file: File): string | null {
  const lower = file.name.toLowerCase()
  if (!ACCEPTED_EXTENSIONS.some((ext) => lower.endsWith(ext))) {
    return 'Chỉ nhận file PDF hoặc DOCX'
  }
  if (file.size > MAX_SIZE_BYTES) {
    return 'File vượt quá 10MB'
  }
  return null
}

export function ResumeUploadDropzone() {
  const uploadMutation = useUploadResumeMutation()
  const [isDragOver, setIsDragOver] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  function handleFile(file: File | undefined) {
    if (!file) {
      return
    }
    const clientError = validateClientSide(file)
    if (clientError) {
      setError(clientError)
      return
    }
    setError(null)
    uploadMutation.mutate(
      { file },
      {
        onError: (err) => setError(extractErrorMessage(err, 'Tải CV thất bại, vui lòng thử lại.')),
      },
    )
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setIsDragOver(false)
    handleFile(event.dataTransfer.files?.[0])
  }

  function handleInputChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    handleFile(file)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      inputRef.current?.click()
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <div
        role="button"
        tabIndex={0}
        aria-label="Tải lên CV, kéo thả hoặc bấm để chọn file"
        onClick={() => inputRef.current?.click()}
        onKeyDown={handleKeyDown}
        onDragOver={(event) => {
          event.preventDefault()
          setIsDragOver(true)
        }}
        onDragLeave={() => setIsDragOver(false)}
        onDrop={handleDrop}
        className={`flex cursor-pointer flex-col items-center justify-center gap-2 rounded-(--radius-card) border-2 border-dashed px-6 py-10 text-center transition-colors ${
          isDragOver ? 'border-brand bg-brand-light' : 'border-line bg-canvas'
        }`}
      >
        <UploadCloud className="h-8 w-8 text-ink-muted" aria-hidden="true" />
        <p className="text-sm text-ink">
          Kéo thả CV vào đây, hoặc <span className="font-medium text-brand">chọn file</span>
        </p>
        <p className="text-xs text-ink-muted">PDF hoặc DOCX, tối đa 10MB</p>
      </div>
      <input ref={inputRef} type="file" accept=".pdf,.docx" className="hidden" onChange={handleInputChange} />
      {uploadMutation.isPending && <p className="text-xs text-ink-muted">Đang tải lên...</p>}
      {error && <p className="text-sm text-danger">{error}</p>}
    </div>
  )
}

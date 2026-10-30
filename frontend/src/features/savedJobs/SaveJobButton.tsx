import { isAxiosError } from 'axios'
import { AlertCircle, Bookmark, BookmarkCheck } from 'lucide-react'
import { useId, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { extractErrorMessage } from '@/lib/httpError'
import { useAuth } from '../auth/useAuth'
import {
  useIsTogglingSavedJob,
  useSaveJobMutation,
  useSavedJobIdsQuery,
  useUnsaveJobMutation,
} from './queries'
import { SAVED_JOB_TEXT, saveJobAria, unsaveJobAria } from './savedJobTexts'

interface SaveJobButtonProps {
  jobId: string
  jobTitle: string
  // icon: nut 40x40 tren the viec lam (UI.md 4a); labeled: nut co chu o /jobs/:id (UI.md 4b).
  variant: 'icon' | 'labeled'
  className?: string
}

// R-B6 - 409 hien nguyen cau backend; loi mang/5xx dung cau co dinh (UI.md muc 7).
function errorText(error: unknown, fallback: string): string {
  if (isAxiosError(error) && error.response?.status === 409) {
    return extractErrorMessage(error, fallback)
  }
  return fallback
}

// FR-U16 R-B2, R-B4, R-B5, R-B6. HR: khong render (UI.md muc 6 "HR | Nut Luu | Khong render (R-B2); khong gui request
// saved-jobs nao (B5)"). Khach: trang thai "chua luu", bam -> /login (khuon ApplyButton), khong goi API. Ung vien: bat/tat
// theo MOT query ids dung chung, cap nhat lac quan (queries.ts). Nut la <button> anh em cua lien ket the, KHONG nam
// trong <a> (R-B3) - khong can preventDefault/stopPropagation.
export function SaveJobButton({ jobId, jobTitle, variant, className }: SaveJobButtonProps) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const statusId = useId()
  const idsQuery = useSavedJobIdsQuery()
  const saveMutation = useSaveJobMutation(jobId)
  const unsaveMutation = useUnsaveJobMutation(jobId)
  const toggling = useIsTogglingSavedJob(jobId)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  if (user?.role === 'HR') {
    return null
  }

  const isGuest = !user
  const saved = !isGuest && (idsQuery.data?.includes(jobId) ?? false)
  const idsUnavailable = !isGuest && (idsQuery.isPending || idsQuery.isError)
  const disabled = !isGuest && (idsUnavailable || toggling)
  const statusMessage = !isGuest && idsQuery.isError ? SAVED_JOB_TEXT.idsLoadError : errorMessage

  function handleClick() {
    if (isGuest) {
      navigate('/login', { state: { from: location } })
      return
    }
    setErrorMessage(null)
    if (saved) {
      unsaveMutation.mutate(undefined, {
        onError: (error) => setErrorMessage(errorText(error, SAVED_JOB_TEXT.unsaveErrorFallback)),
      })
    } else {
      saveMutation.mutate(undefined, {
        onError: (error) => setErrorMessage(errorText(error, SAVED_JOB_TEXT.saveErrorFallback)),
      })
    }
  }

  const Icon = saved ? BookmarkCheck : Bookmark
  const status = statusMessage && (
    <p id={statusId} role="status" className="flex items-center gap-1 text-xs text-m3-on-surface">
      <AlertCircle className="h-3.5 w-3.5 shrink-0 text-m3-error" aria-hidden="true" />
      {statusMessage}
    </p>
  )

  if (variant === 'labeled') {
    return (
      <div className={`flex flex-col gap-1 ${className ?? ''}`}>
        <Button
          type="button"
          variant="outline"
          className="h-11 px-6"
          aria-pressed={saved}
          aria-label={saved ? SAVED_JOB_TEXT.unsaveThisJobAria : undefined}
          aria-describedby={statusMessage ? statusId : undefined}
          disabled={disabled}
          onClick={handleClick}
        >
          <Icon aria-hidden="true" />
          {saved ? SAVED_JOB_TEXT.savedLabeled : SAVED_JOB_TEXT.saveLabeled}
        </Button>
        {status}
      </div>
    )
  }

  return (
    <div className={`flex flex-col items-end gap-1 ${className ?? ''}`}>
      <button
        type="button"
        aria-pressed={saved}
        aria-label={saved ? unsaveJobAria(jobTitle) : saveJobAria(jobTitle)}
        aria-describedby={statusMessage ? statusId : undefined}
        disabled={disabled}
        onClick={handleClick}
        className={`inline-flex h-10 w-10 items-center justify-center rounded-(--radius-badge) border transition disabled:cursor-not-allowed disabled:opacity-60 ${
          saved
            ? 'border-m3-primary-container bg-m3-primary-container text-m3-on-primary-container'
            : 'border-m3-outline text-m3-on-surface-variant hover:bg-m3-surface-container'
        }`}
      >
        <Icon className="h-5 w-5" aria-hidden="true" />
      </button>
      {status}
    </div>
  )
}

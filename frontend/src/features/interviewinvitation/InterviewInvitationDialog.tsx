import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { AlertTriangle } from 'lucide-react'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import type { ApplicationHrListItem } from '../scoring/types'
import { useInterviewInvitationPreviewQuery, useSendInterviewInvitationMutation } from './queries'

function extractErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const data = err.response?.data as { message?: unknown } | undefined
    if (data && typeof data.message === 'string' && data.message.length > 0) {
      return data.message
    }
  }
  return fallback
}

const inviteSchema = z.object({
  scheduledAt: z
    .string()
    .min(1, 'Vui lòng chọn ngày giờ phỏng vấn')
    .refine((v) => new Date(v).getTime() > Date.now(), 'Thời gian phỏng vấn phải ở trong tương lai'),
  location: z.string().optional(),
  subject: z.string().trim().min(1, 'Vui lòng nhập tiêu đề thư mời').max(255, 'Tối đa 255 ký tự'),
  content: z.string().trim().min(1, 'Vui lòng nhập nội dung thư mời').max(10000, 'Tối đa 10000 ký tự'),
})

type InviteFormValues = z.infer<typeof inviteSchema>

// <input type="datetime-local"> tra ve gio theo MUI GIO TRINH DUYET cua HR, khong kem thong tin
// timezone trong chuoi (vd "2026-08-26T10:00"). new Date(value) parse chuoi do theo local time cua
// may dang chay, .toISOString() quy doi sang UTC (Instant backend yeu cau). Day la HANH VI MONG
// MUON (HR go "10:00" nghia la 10:00 gio cua ho, khong phai UTC) - KHONG phai bug, dung sua nham
// thanh cach doc gio khac.
function buildScheduledAtIso(datetimeLocalValue: string): string {
  return new Date(datetimeLocalValue).toISOString()
}

interface InterviewInvitationDialogProps {
  application: ApplicationHrListItem | null
  jobId: string
  onOpenChange: (open: boolean) => void
}

export function InterviewInvitationDialog({ application, jobId, onOpenChange }: InterviewInvitationDialogProps) {
  const open = application !== null
  const previewQuery = useInterviewInvitationPreviewQuery(application?.id, open)
  const sendMutation = useSendInterviewInvitationMutation(jobId)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<InviteFormValues>({
    resolver: zodResolver(inviteSchema),
    defaultValues: { scheduledAt: '', location: '', subject: '', content: '' },
  })

  // Nap subject/content mac dinh tu ban render preview ngay khi API tra ve - HR van sua duoc tu do
  // truoc khi gui (dung y SRS: "co the chinh sua noi dung truoc khi gui").
  useEffect(() => {
    if (previewQuery.data) {
      reset({
        scheduledAt: '',
        location: '',
        subject: previewQuery.data.subject,
        content: previewQuery.data.content,
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [previewQuery.data])

  useEffect(() => {
    if (!open) {
      reset({ scheduledAt: '', location: '', subject: '', content: '' })
      sendMutation.reset()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const onSubmit = handleSubmit(async (values) => {
    if (!application) return
    await sendMutation.mutateAsync({
      applicationId: application.id,
      payload: {
        scheduledAt: buildScheduledAtIso(values.scheduledAt),
        location: values.location?.trim() ? values.location.trim() : undefined,
        subject: values.subject.trim(),
        content: values.content.trim(),
      },
    })
    onOpenChange(false)
  })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Mời phỏng vấn{application ? ` — ${application.candidateName}` : ''}</DialogTitle>
          <DialogDescription>
            Nội dung đã render sẵn từ mẫu giấy mời của tin tuyển dụng này. Bạn có thể sửa lại trước khi gửi.
          </DialogDescription>
        </DialogHeader>

        {previewQuery.isLoading && <p className="text-sm text-ink-muted">Đang tải nội dung mẫu...</p>}
        {previewQuery.isError && (
          <p className="text-sm text-danger">Không tải được nội dung mẫu, vui lòng đóng và thử lại.</p>
        )}

        {previewQuery.data && (
          <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
            {previewQuery.data.companyNameMismatch && (
              <div className="flex gap-2 rounded-(--radius-card) border border-warning bg-canvas p-3">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning" aria-hidden="true" />
                <p className="text-sm text-ink">
                  Tên công ty trong mẫu giấy mời (<span className="font-medium">{previewQuery.data.templateCompanyName}</span>)
                  khác với tên công ty hiện tại (
                  <span className="font-medium">{previewQuery.data.currentCompanyName}</span>). Hệ thống không tự sửa —
                  vui lòng kiểm tra nội dung bên dưới trước khi gửi.
                </p>
              </div>
            )}

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="invite-scheduled-at">Ngày giờ phỏng vấn</Label>
                <Input id="invite-scheduled-at" type="datetime-local" {...register('scheduledAt')} />
                {errors.scheduledAt && <p className="text-sm text-danger">{errors.scheduledAt.message}</p>}
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="invite-location">Địa điểm (tuỳ chọn)</Label>
                <Input id="invite-location" {...register('location')} />
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="invite-subject">Tiêu đề thư mời</Label>
              <Input id="invite-subject" {...register('subject')} />
              {errors.subject && <p className="text-sm text-danger">{errors.subject.message}</p>}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="invite-content">Nội dung thư mời</Label>
              <Textarea id="invite-content" rows={8} {...register('content')} />
              {errors.content && <p className="text-sm text-danger">{errors.content.message}</p>}
            </div>

            {sendMutation.isError && (
              <p className="text-sm text-danger">
                {extractErrorMessage(sendMutation.error, 'Gửi lời mời thất bại, vui lòng thử lại.')}
              </p>
            )}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={sendMutation.isPending}>
                Huỷ
              </Button>
              <Button type="submit" disabled={isSubmitting || sendMutation.isPending}>
                {sendMutation.isPending ? 'Đang gửi...' : 'Gửi lời mời'}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}

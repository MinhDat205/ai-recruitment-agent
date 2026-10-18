import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient, type QueryKey } from '@tanstack/react-query'
import { AlertTriangle, Info } from 'lucide-react'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { extractErrorMessage } from '../../lib/httpError'
import { applicationDetailKey } from '../applicationDetail/queries'
import { BLIND_REVIEW_TEXT, CANDIDATE_NAME_PLACEHOLDER } from '../blindReview/blindReviewLabels'
import { MESSAGE_CONVERSATIONS_KEY_PREFIX, messageThreadKey } from '../messages/queries'
import { InterviewSlotFields, type InvitationFormValues } from './InterviewSlotFields'
import { formatSlotTime, INVITATION_TEXT, SLOT_FORM_ERRORS, SLOT_LOCATION_MAX } from './invitationLabels'
import {
  useInterviewInvitationPreviewQuery,
  useResendInterviewInvitationMutation,
  useSendInterviewInvitationMutation,
} from './queries'
import type { InterviewInvitationResponse, InterviewInvitationSendRequest } from './types'

// Thoi diem cua input datetime-local tinh toi phut (khop backend cat ve dau phut, R-S3) - dung de so trung khung.
function minuteKey(datetimeLocalValue: string): number {
  return Math.floor(new Date(datetimeLocalValue).getTime() / 60000)
}

// FR-H12 UI.md muc 7 - loi tung khung (Zod). Kiem tren gia tri da bo khoang trang hai dau nhu backend (R-S6). Gioi han
// so khung KHONG kiem o day: nut "Them khung gio" khoa theo maxSlots, backend la chot chan (R-S8).
const inviteSchema = z.object({
  slots: z
    .array(z.object({ scheduledAt: z.string(), location: z.string() }))
    .superRefine((slots, ctx) => {
      const seen = new Map<number, number>()
      slots.forEach((slot, index) => {
        if (slot.scheduledAt.length === 0) {
          ctx.addIssue({ code: 'custom', path: [index, 'scheduledAt'], message: SLOT_FORM_ERRORS.timeRequired })
        } else if (new Date(slot.scheduledAt).getTime() <= Date.now()) {
          ctx.addIssue({ code: 'custom', path: [index, 'scheduledAt'], message: SLOT_FORM_ERRORS.timePast })
        } else {
          const key = minuteKey(slot.scheduledAt)
          const first = seen.get(key)
          if (first === undefined) {
            seen.set(key, index)
          } else {
            ctx.addIssue({
              code: 'custom',
              path: [index, 'scheduledAt'],
              message: SLOT_FORM_ERRORS.timeDuplicate(first + 1),
            })
          }
        }
        const location = slot.location.trim()
        if (location.length === 0) {
          ctx.addIssue({ code: 'custom', path: [index, 'location'], message: SLOT_FORM_ERRORS.locationRequired })
        } else if (location.length > SLOT_LOCATION_MAX) {
          ctx.addIssue({ code: 'custom', path: [index, 'location'], message: SLOT_FORM_ERRORS.locationTooLong })
        }
      })
    }),
  subject: z.string().trim().min(1, 'Vui lòng nhập tiêu đề thư mời').max(255, 'Tối đa 255 ký tự'),
  content: z.string().trim().min(1, 'Vui lòng nhập nội dung thư mời').max(10000, 'Tối đa 10000 ký tự'),
})

const EMPTY_FORM: InvitationFormValues = { slots: [{ scheduledAt: '', location: '' }], subject: '', content: '' }

// <input type="datetime-local"> tra ve gio theo MUI GIO TRINH DUYET cua HR, khong kem thong tin
// timezone trong chuoi (vd "2026-08-26T10:00"). new Date(value) parse chuoi do theo local time cua
// may dang chay, .toISOString() quy doi sang UTC (Instant backend yeu cau). Day la HANH VI MONG
// MUON (HR go "10:00" nghia la 10:00 gio cua ho, khong phai UTC) - KHONG phai bug, dung sua nham
// thanh cach doc gio khac.
function buildScheduledAtIso(datetimeLocalValue: string): string {
  return new Date(datetimeLocalValue).toISOString()
}

function toPayload(values: InvitationFormValues): InterviewInvitationSendRequest {
  return {
    slots: values.slots.map((slot) => ({
      scheduledAt: buildScheduledAtIso(slot.scheduledAt),
      location: slot.location.trim(),
    })),
    subject: values.subject.trim(),
    content: values.content.trim(),
  }
}

// FR-H09 R-A4: prop toi thieu { id, candidateName } (truoc la ca ApplicationHrListItem) de trang ho so
// don dung duoc ma khong can dong cua danh sach theo Job. invalidateQueryKeys: query THEM can lam moi
// sau khi gui thanh cong (mac dinh rong).
// FR-H12 (UI.md 4a, 4b): mode 'invite' (mac dinh - noi goi cu ApplicationActionBar khong doi) render tu preview, maxSlots
// tu preview; mode 'resend' dien san tieu de + noi dung tu giay moi hieu luc (activeInvitation, du lieu I1), maxSlots tu
// I1, KHONG goi preview (Q6). onResent: goi sau khi gui lai thanh cong (tab dua focus ve tieu de "Giay moi hien tai").
interface InterviewInvitationDialogProps {
  application: { id: string; candidateName: string } | null
  jobId: string
  onOpenChange: (open: boolean) => void
  invalidateQueryKeys?: QueryKey[]
  mode?: 'invite' | 'resend'
  activeInvitation?: InterviewInvitationResponse | null
  maxSlots?: number
  onResent?: () => void
}

export function InterviewInvitationDialog({
  application,
  jobId,
  onOpenChange,
  invalidateQueryKeys,
  mode = 'invite',
  activeInvitation = null,
  maxSlots: resendMaxSlots,
  onResent,
}: InterviewInvitationDialogProps) {
  const open = application !== null
  const isResend = mode === 'resend'
  const previewQuery = useInterviewInvitationPreviewQuery(application?.id, open && !isResend)
  const sendMutation = useSendInterviewInvitationMutation(jobId, invalidateQueryKeys)
  const resendMutation = useResendInterviewInvitationMutation(invalidateQueryKeys)
  const mutation = isResend ? resendMutation : sendMutation
  const queryClient = useQueryClient()

  const {
    control,
    register,
    handleSubmit,
    reset,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<InvitationFormValues>({
    resolver: zodResolver(inviteSchema),
    defaultValues: EMPTY_FORM,
  })

  // Moi phong van: nap subject/content tu ban render preview ngay khi API tra ve - HR van sua duoc tu do
  // truoc khi gui (dung y SRS: "co the chinh sua noi dung truoc khi gui"). Mot khung trong.
  useEffect(() => {
    if (!isResend && previewQuery.data) {
      reset({ ...EMPTY_FORM, subject: previewQuery.data.subject, content: previewQuery.data.content })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [previewQuery.data, isResend])

  // Gui lai (Q6): tieu de + noi dung cua giay moi HIEU LUC, khung gio de trong (mot khung trong).
  useEffect(() => {
    if (open && isResend && activeInvitation) {
      reset({ ...EMPTY_FORM, subject: activeInvitation.subject, content: activeInvitation.renderedContent })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, isResend, activeInvitation?.id])

  useEffect(() => {
    if (!open) {
      reset(EMPTY_FORM)
      sendMutation.reset()
      resendMutation.reset()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const onSubmit = handleSubmit(async (values) => {
    if (!application) return
    await mutation.mutateAsync({ applicationId: application.id, payload: toPayload(values) })
    if (!isResend) {
      // FR-H16 UI.md 4f - gui lan dau bo an danh (R-I4): lam moi moi query cua trang ho so don (E1-E5, CV da trich xuat -
      // tien to chung cua applicationDetailKey), cuoc trao doi va hop thu HR de ten that hien ngay, bat ke hop thoai mo
      // tu dau (ho so don, tab Ung vien, trang so sanh). Danh sach theo Job da lam moi trong sendMutation.
      queryClient.invalidateQueries({ queryKey: applicationDetailKey(application.id).slice(0, 2) })
      queryClient.invalidateQueries({ queryKey: messageThreadKey('hr', application.id) })
      queryClient.invalidateQueries({ queryKey: [MESSAGE_CONVERSATIONS_KEY_PREFIX] })
    }
    onOpenChange(false)
    if (isResend) {
      onResent?.()
    }
  })

  const maxSlots = isResend ? resendMaxSlots : previewQuery.data?.maxSlots
  const formReady = isResend ? Boolean(activeInvitation && maxSlots) : Boolean(previewQuery.data)
  const confirmedSlot = activeInvitation?.slots.find((slot) => slot.id === activeInvitation.confirmedSlotId)
  const candidateName = application?.candidateName ?? ''
  // FR-H16 UI.md 4f - MOT dieu kien cho ca ba diem mo (ho so don, tab Ung vien, so sanh): preview con nguyen chu
  // placeholder (backend giu khi don dang an danh - R-I1). Khong chan HR xoa cho nay.
  const showsBlindNotice =
    !isResend &&
    Boolean(
      previewQuery.data &&
        (previewQuery.data.subject.includes(CANDIDATE_NAME_PLACEHOLDER) ||
          previewQuery.data.content.includes(CANDIDATE_NAME_PLACEHOLDER)),
    )

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle className="break-words">
            {isResend ? INVITATION_TEXT.resendTitle(candidateName) : INVITATION_TEXT.inviteTitle(candidateName)}
          </DialogTitle>
          {isResend ? (
            <div
              role="note"
              className="flex items-start gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container px-4 py-3 text-sm text-m3-on-surface"
            >
              <Info className="mt-0.5 h-4 w-4 shrink-0 text-m3-on-surface" aria-hidden="true" />
              <div className="flex min-w-0 flex-col gap-1 break-words">
                <DialogDescription className="text-m3-on-surface">{INVITATION_TEXT.resendNotice}</DialogDescription>
                {confirmedSlot && <p>{INVITATION_TEXT.resendConfirmedNotice(formatSlotTime(confirmedSlot.scheduledAt))}</p>}
              </div>
            </div>
          ) : (
            <DialogDescription>
              Nội dung đã render sẵn từ mẫu giấy mời của tin tuyển dụng này. Bạn có thể sửa lại trước khi gửi.
            </DialogDescription>
          )}
        </DialogHeader>

        {showsBlindNotice && (
          <div
            role="note"
            className="flex items-start gap-3 rounded-m3-sm border border-m3-outline-variant bg-m3-surface-container px-4 py-3 text-sm text-m3-on-surface"
          >
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-m3-on-surface" aria-hidden="true" />
            <p className="min-w-0 break-words">{BLIND_REVIEW_TEXT.invitationNotice}</p>
          </div>
        )}

        {!isResend && previewQuery.isLoading && (
          <p className="text-sm text-m3-on-surface-variant">Đang tải nội dung mẫu...</p>
        )}
        {!isResend && previewQuery.isError && (
          <p className="text-sm text-m3-error">Không tải được nội dung mẫu, vui lòng đóng và thử lại.</p>
        )}

        {formReady && maxSlots !== undefined && (
          <form onSubmit={onSubmit} noValidate className="flex min-w-0 flex-col gap-4">
            {!isResend && previewQuery.data?.companyNameMismatch && (
              <div className="flex gap-2 rounded-(--radius-card) border border-m3-warning bg-m3-surface p-3">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-m3-warning" aria-hidden="true" />
                <p className="text-sm text-m3-on-surface">
                  Tên công ty trong mẫu giấy mời (<span className="font-medium">{previewQuery.data.templateCompanyName}</span>)
                  khác với tên công ty hiện tại (
                  <span className="font-medium">{previewQuery.data.currentCompanyName}</span>). Hệ thống không tự sửa —
                  vui lòng kiểm tra nội dung bên dưới trước khi gửi.
                </p>
              </div>
            )}

            <InterviewSlotFields
              control={control}
              register={register}
              errors={errors}
              setFocus={setFocus}
              maxSlots={maxSlots}
              disabled={mutation.isPending}
            />

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="invite-subject">Tiêu đề thư mời</Label>
              <Input id="invite-subject" {...register('subject')} />
              {errors.subject && <p className="text-sm text-m3-error">{errors.subject.message}</p>}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="invite-content">Nội dung thư mời</Label>
              <Textarea id="invite-content" rows={8} {...register('content')} />
              {errors.content && <p className="text-sm text-m3-error">{errors.content.message}</p>}
            </div>

            {mutation.isError && (
              <p className="text-sm break-words text-m3-error">
                {extractErrorMessage(
                  mutation.error,
                  isResend ? INVITATION_TEXT.resendFailed : INVITATION_TEXT.inviteFailed,
                )}
              </p>
            )}

            {/* UI.md 4e, muc 8 - compact: Huy/Gui cung hang, chia deu chieu ngang. */}
            <DialogFooter className="flex-row justify-end *:flex-1 sm:*:flex-none">
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={mutation.isPending}>
                Huỷ
              </Button>
              <Button type="submit" disabled={isSubmitting || mutation.isPending}>
                {mutation.isPending
                  ? INVITATION_TEXT.submitting
                  : isResend
                    ? INVITATION_TEXT.submitResend
                    : INVITATION_TEXT.submitInvite}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}

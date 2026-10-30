import { Fragment, type Ref } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { formatDateVi } from '../../lib/date'
import { HR_RECONSIDERATION_TEXT as TEXT, RECONSIDERATION_STATUS_LABELS } from './reconsiderationLabels'
import type { HrReconsideration, ReconsiderationTarget } from './types'

interface ReconsiderationCardProps {
  item: HrReconsideration
  // 'tab' (UI.md 4e): tieu de h3 la ten ung vien, du bon moc ngay, lien ket "Mở hồ sơ".
  // 'detail' (UI.md 4f): tieu de h2 "Yêu cầu xem xét lại", bo "Nộp đơn" (dau trang da co) va "Mở hồ sơ".
  variant: 'tab' | 'detail'
  onInvite: (target: ReconsiderationTarget) => void
  onDecline: (target: ReconsiderationTarget) => void
  headingRef?: Ref<HTMLHeadingElement>
}

// Doan van ban nguoi dung go (ly do / phan hoi): hien DAY DU, nguyen van, khong render HTML (R-V4). Don an danh: backend
// da che san (R-A2), frontend khong xu ly gi them.
function QuotedText({ label, text, emptyText }: { label: string; text: string | null; emptyText: string }) {
  return (
    <div className="flex flex-col gap-1">
      <p className="text-sm text-m3-on-surface-variant">{label}</p>
      {text !== null ? (
        <p className="text-m3-body-md whitespace-pre-wrap break-words text-m3-on-surface">{text}</p>
      ) : (
        <p className="text-sm text-m3-on-surface-variant">{emptyText}</p>
      )}
    </div>
  )
}

// The mot yeu cau xem xet lai phia HR (FR-C09 UI.md 4e, 4f, 4h). Chip trang thai trung tinh, luon la chu (UI.md 5d, muc
// 10): khong xanh cho "Đã mời", khong do cho "Đã giữ từ chối". KHONG co diem/hang. Yeu cau OPEN co hai nut cung muc nhan
// manh (outline). Hai hop thoai KHONG nam trong the (useReconsiderationDialogs o noi goi): bo loc "Đang chờ" lam the bien
// mat ngay khi yeu cau vua quyet, hop thoai trong the se bi go truoc khi kip dong va tra focus.
export function ReconsiderationCard({ item, variant, onInvite, onDecline, headingRef }: ReconsiderationCardProps) {
  const isOpen = item.status === 'OPEN'
  const target = { applicationId: item.applicationId, candidateName: item.candidateName }
  const HeadingTag = variant === 'tab' ? 'h3' : 'h2'
  const title = variant === 'tab' ? item.candidateName : TEXT.blockTitle

  const dates = [
    variant === 'tab' ? TEXT.appliedOn(formatDateVi(item.appliedAt)) : null,
    item.rejectedAt ? TEXT.rejectedOn(formatDateVi(item.rejectedAt)) : null,
    TEXT.requestedOn(formatDateVi(item.requestedAt)),
    item.decidedAt ? TEXT.decidedOn(formatDateVi(item.decidedAt)) : null,
  ].filter((text): text is string => text !== null)

  return (
    <article className="flex min-w-0 flex-col gap-3 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-4">
      <div className="flex min-w-0 flex-col items-start gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
        <HeadingTag
          ref={headingRef}
          tabIndex={-1}
          className="min-w-0 text-m3-title-md break-words text-m3-on-surface outline-none focus-visible:ring-2 focus-visible:ring-m3-primary"
        >
          {title}
        </HeadingTag>
        <span className="inline-flex shrink-0 items-center rounded-(--radius-badge) bg-m3-surface-container px-2 py-1 text-xs font-medium text-m3-on-surface">
          {RECONSIDERATION_STATUS_LABELS[item.status]}
        </span>
      </div>

      {/* >= sm: mot dong ngan " · "; < sm: xep doc, bo dau ngan (UI.md 4h). */}
      <p className="flex flex-col text-sm text-m3-on-surface-variant sm:flex-row sm:flex-wrap sm:gap-x-2">
        {dates.map((text, index) => (
          <Fragment key={text}>
            {index > 0 && (
              <span aria-hidden="true" className="hidden sm:inline">
                ·
              </span>
            )}
            <span>{text}</span>
          </Fragment>
        ))}
      </p>

      <QuotedText label={TEXT.reasonLabel} text={item.reason} emptyText={TEXT.noReason} />
      {item.status === 'DECLINED' && (
        <QuotedText label={TEXT.hrResponseLabel} text={item.hrResponse} emptyText={TEXT.noHrResponse} />
      )}

      {(isOpen || variant === 'tab') && (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          {variant === 'tab' && (
            <Link
              to={`/hr/applications/${item.applicationId}`}
              className="order-last inline-flex min-h-12 items-center self-start text-sm text-m3-primary hover:underline sm:order-first sm:min-h-0"
            >
              {TEXT.openProfile}
            </Link>
          )}
          {isOpen && (
            <div className="flex flex-col gap-2 sm:ml-auto sm:flex-row">
              <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => onInvite(target)}>
                {TEXT.invite}
              </Button>
              <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={() => onDecline(target)}>
                {TEXT.decline}
              </Button>
            </div>
          )}
        </div>
      )}
    </article>
  )
}


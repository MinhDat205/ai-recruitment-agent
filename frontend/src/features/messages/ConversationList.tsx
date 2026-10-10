import { AlertCircle, Paperclip } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { formatDateTimeVi } from '../../lib/date'
import { ApplicationStatusBadge } from '../applications/ApplicationStatusBadge'
import { Pagination } from '../jobs/Pagination'
import { useConversationsQuery } from './queries'
import type { ConversationCandidate, ConversationHr, MessageSide } from './types'

// Mot dong hop thu da chuan hoa cho hai phia. counterpartName: phia HR la ho ten ung vien, phia ung vien la TEN
// CONG TY - khong bao gio ho ten/email HR (R-I2, R-G2; backend cung khong tra cac truong do).
interface ConversationRowData {
  applicationId: string
  counterpartName: string
  jobTitle: string
  applicationStatus: ConversationHr['applicationStatus']
  lastMessageAt: string
  lastMessageExcerpt: string | null
  lastMessageMine: boolean
  lastMessageHasAttachment: boolean
  unreadCount: number
}

function toRow(item: ConversationHr | ConversationCandidate): ConversationRowData {
  const counterpartName = 'candidateName' in item ? item.candidateName : item.companyName
  return {
    applicationId: item.applicationId,
    counterpartName,
    jobTitle: item.jobTitle,
    applicationStatus: item.applicationStatus,
    lastMessageAt: item.lastMessageAt,
    lastMessageExcerpt: item.lastMessageExcerpt,
    lastMessageMine: item.lastMessageMine,
    lastMessageHasAttachment: item.lastMessageHasAttachment,
    unreadCount: item.unreadCount,
  }
}

// ?page= bat dau tu 0; gia tri la (khong phai so nguyen khong am) -> 0 (UI.md muc 2).
function parsePage(value: string | null): number {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0
}

const EMPTY_STATE: Record<MessageSide, { text: string; linkLabel: string; linkTo: string }> = {
  hr: { text: 'Chưa có cuộc trao đổi nào.', linkLabel: 'Xem danh sách ứng viên', linkTo: '/hr/candidates' },
  candidate: {
    text: 'Bạn chưa có cuộc trao đổi nào.',
    linkLabel: 'Xem đơn ứng tuyển',
    linkTo: '/candidate/applications',
  },
}

function applicationLink(side: MessageSide, applicationId: string): string {
  return side === 'hr'
    ? `/hr/applications/${applicationId}?tab=messages`
    : `/candidate/applications/${applicationId}?tab=messages`
}

// Badge so tin chua doc: m3-primary (KHONG do - tin chua doc khong phai loi), kem aria-label (R-G3, UI.md muc 9).
function UnreadBadge({ count }: { count: number }) {
  return (
    <span
      aria-label={`${count} tin chưa đọc`}
      className="inline-flex min-w-6 shrink-0 items-center justify-center rounded-(--radius-badge) bg-m3-primary px-1.5 py-0.5 text-xs font-medium text-m3-on-primary"
    >
      {count}
    </span>
  )
}

// Dong doan trich: "Bạn: " khi tin moi nhat cua minh; tin chi co tep -> icon + "Tệp đính kèm".
function LastMessagePreview({ row }: { row: ConversationRowData }) {
  return (
    <p className="min-w-0 flex-1 text-sm break-words text-m3-on-surface">
      {row.lastMessageMine && 'Bạn: '}
      {row.lastMessageExcerpt !== null ? (
        row.lastMessageExcerpt
      ) : (
        <span className="inline-flex items-center gap-1 align-middle">
          <Paperclip className="h-4 w-4 shrink-0" aria-hidden="true" />
          Tệp đính kèm
        </span>
      )}
    </p>
  )
}

// Mot dong = MOT Link duy nhat phu ca dong (UI.md muc 4d, 9), vung cham >= 48px. Trong dong co hover doi nen nen
// moi chu dung m3-on-surface - KHONG dung m3-on-surface-variant/m3-primary (UI.md muc 5d, UI_GUIDE muc 6).
function ConversationRow({ side, row }: { side: MessageSide; row: ConversationRowData }) {
  const unread = row.unreadCount > 0
  const time = formatDateTimeVi(row.lastMessageAt)
  // Ten truy cap ghep tu ten ben kia + ten tin; them so tin chua doc de trinh doc man hinh khong mat thong tin
  // cua badge (aria-label cua Link thay the noi dung ben trong).
  const accessibleName = `${row.counterpartName}, ${row.jobTitle}${unread ? `, ${row.unreadCount} tin chưa đọc` : ''}`

  return (
    <li>
      <Link
        to={applicationLink(side, row.applicationId)}
        aria-label={accessibleName}
        className="flex min-h-12 flex-col gap-1 bg-m3-surface px-4 py-3 text-m3-on-surface hover:bg-m3-surface-container sm:px-6"
      >
        <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
          <span className={`min-w-0 break-words ${unread ? 'font-semibold' : 'font-medium'}`}>
            {row.counterpartName}
          </span>
          {/* Compact (< sm): badge so o cuoi dong ten (UI.md muc 4e, 8). */}
          {unread && (
            <span className="sm:hidden">
              <UnreadBadge count={row.unreadCount} />
            </span>
          )}
          <span className="hidden flex-wrap items-center gap-3 sm:flex">
            <ApplicationStatusBadge status={row.applicationStatus} />
            <time dateTime={row.lastMessageAt} className="text-sm">
              {time}
            </time>
          </span>
        </div>
        <p className="text-sm break-words">{row.jobTitle}</p>
        <span className="sm:hidden">
          <ApplicationStatusBadge status={row.applicationStatus} />
        </span>
        <div className="flex items-start justify-between gap-3">
          <LastMessagePreview row={row} />
          {unread && (
            <span className="hidden sm:inline-flex">
              <UnreadBadge count={row.unreadCount} />
            </span>
          )}
        </div>
        <time dateTime={row.lastMessageAt} className="text-sm sm:hidden">
          {time}
        </time>
      </Link>
    </li>
  )
}

function ListSkeleton() {
  return (
    <ul className="divide-y divide-m3-outline-variant" aria-hidden="true">
      {[0, 1, 2].map((index) => (
        <li key={index} className="flex flex-col gap-2 px-4 py-3 sm:px-6">
          <div className="h-4 w-1/3 animate-pulse rounded bg-m3-surface-container" />
          <div className="h-4 w-1/2 animate-pulse rounded bg-m3-surface-container" />
          <div className="h-4 w-2/3 animate-pulse rounded bg-m3-surface-container" />
        </li>
      ))}
    </ul>
  )
}

// Hop thu "Tin nhan" dung chung hai phia (R-C2, UI.md muc 4d, 4e, 6). Danh sach lien ket sang tab "Trao doi"
// cua tung don - khong doc/gui tin ngay tai day (R-P4), KHONG goi M3 (R-I4). Tu tai lai 30 giay, tu dung sau 20
// phut (R-A1, R-A2). Khong hien diem/thu hang/tieu chi (R-G2).
export function ConversationList({ side }: { side: MessageSide }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const page = parsePage(searchParams.get('page'))
  const conversationsQuery = useConversationsQuery(side, page)

  function handlePageChange(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    setSearchParams(next)
  }

  if (conversationsQuery.isLoading) {
    return <ListSkeleton />
  }

  const data = conversationsQuery.data

  // Chi coi la loi khi CHUA co du lieu - lan tai lai ngam loi thi giu nguyen danh sach dang hien.
  if (!data) {
    return (
      <div className="flex flex-col items-start gap-2 px-4 py-3 sm:px-6">
        <p role="alert" className="flex items-start gap-1.5 text-sm text-m3-on-surface">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
          Không tải được danh sách tin nhắn.
        </p>
        <Button type="button" variant="outline" size="sm" onClick={() => conversationsQuery.refetch()}>
          Thử lại
        </Button>
      </div>
    )
  }

  const rows = data.items.map(toRow)
  const empty = EMPTY_STATE[side]

  return (
    <div className="flex flex-col">
      {conversationsQuery.timedOut && (
        <div className="flex flex-wrap items-center gap-2 px-4 pb-3 sm:px-6">
          <p className="text-sm text-m3-on-surface-variant">Đã tạm dừng tự cập nhật.</p>
          <Button type="button" variant="outline" size="sm" onClick={conversationsQuery.resumePolling}>
            Tải lại
          </Button>
        </div>
      )}

      {rows.length === 0 ? (
        <div className="flex flex-col items-start gap-2 px-4 py-3 sm:px-6">
          <p className="text-sm text-m3-on-surface">{empty.text}</p>
          <Link to={empty.linkTo} className="text-sm font-medium text-m3-primary hover:underline">
            {empty.linkLabel}
          </Link>
        </div>
      ) : (
        <ul className="divide-y divide-m3-outline-variant border-t border-m3-outline-variant">
          {rows.map((row) => (
            <ConversationRow key={row.applicationId} side={side} row={row} />
          ))}
        </ul>
      )}

      <Pagination page={page} totalPages={data.totalPages} onPageChange={handlePageChange} />
    </div>
  )
}

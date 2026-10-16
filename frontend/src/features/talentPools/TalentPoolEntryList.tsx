import { Link } from 'react-router-dom'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDateVi } from '../../lib/date'
import { ApplicationStatusBadge } from '../applications/ApplicationStatusBadge'
import { TALENT_POOL_COLUMNS, TALENT_POOL_TEXT } from './talentPoolLabels'
import type { TalentPoolEntry } from './types'

// FR-H15 UI.md 4b (bang, >= md) / 4c (the, < md). Hai bo cuc chon bang CSS (hidden/md:block): phan bi an la
// display:none nen khong vao cay truy cap, va khong co id nao bi trung. KHONG cot diem, hang, minh chung (R-A2) - danh gia
// AI, CV, trao doi xem bang cach mo trang ho so don co san (FR-H09).
interface TalentPoolEntryListProps {
  entries: TalentPoolEntry[]
  onRemove: (entry: TalentPoolEntry) => void
}

function applicationHref(entry: TalentPoolEntry): string {
  return `/hr/applications/${entry.applicationId}`
}

// Tab "Trao doi" cua trang ho so don - noi co san nut "Soan bang AI" cua FR-C07.
function messagesHref(entry: TalentPoolEntry): string {
  return `/hr/applications/${entry.applicationId}?tab=messages`
}

const actionLinkClass =
  'inline-flex min-h-12 items-center rounded-md px-2 text-sm font-medium text-m3-primary hover:bg-m3-surface-container hover:underline focus-visible:ring-2 focus-visible:ring-m3-primary focus-visible:outline-none md:h-8 md:min-h-0'

const removeButtonClass =
  'inline-flex min-h-12 items-center rounded-md px-2 text-sm font-medium text-m3-on-surface-variant hover:bg-m3-surface-container hover:text-m3-on-surface focus-visible:ring-2 focus-visible:ring-m3-primary focus-visible:outline-none md:h-8 md:min-h-0'

function JobCell({ entry }: { entry: TalentPoolEntry }) {
  return (
    <div className="flex min-w-0 flex-col">
      <span className="break-words text-m3-on-surface">{entry.jobTitle}</span>
      {entry.jobDeleted && <span className="text-sm text-m3-on-surface-variant">{TALENT_POOL_TEXT.jobDeleted}</span>}
    </div>
  )
}

function Actions({ entry, onRemove }: { entry: TalentPoolEntry; onRemove: (entry: TalentPoolEntry) => void }) {
  return (
    <div className="flex flex-wrap items-center gap-1">
      <Link
        to={applicationHref(entry)}
        aria-label={TALENT_POOL_TEXT.openApplicationLabel(entry.candidateName)}
        className={actionLinkClass}
      >
        {TALENT_POOL_TEXT.openApplication}
      </Link>
      <Link
        to={messagesHref(entry)}
        aria-label={TALENT_POOL_TEXT.messagesLabel(entry.candidateName)}
        className={actionLinkClass}
      >
        {TALENT_POOL_TEXT.messages}
      </Link>
      <button
        type="button"
        aria-label={TALENT_POOL_TEXT.removeFromPoolLabel(entry.candidateName)}
        className={removeButtonClass}
        onClick={() => onRemove(entry)}
      >
        {TALENT_POOL_TEXT.removeFromPool}
      </button>
    </div>
  )
}

export function TalentPoolEntryList({ entries, onRemove }: TalentPoolEntryListProps) {
  return (
    <>
      {/* >= md: bang trong vung cuon ngang rieng, khong cuon ngang <body> (UI.md muc 8). */}
      <div className="hidden overflow-x-auto rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{TALENT_POOL_COLUMNS.candidate}</TableHead>
              <TableHead>{TALENT_POOL_COLUMNS.job}</TableHead>
              <TableHead>{TALENT_POOL_COLUMNS.status}</TableHead>
              <TableHead>{TALENT_POOL_COLUMNS.appliedAt}</TableHead>
              <TableHead>{TALENT_POOL_COLUMNS.addedAt}</TableHead>
              <TableHead>{TALENT_POOL_COLUMNS.actions}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {entries.map((entry) => (
              <TableRow key={entry.applicationId}>
                <TableCell className="align-top">
                  <Link to={applicationHref(entry)} className="break-words text-m3-primary hover:underline">
                    {entry.candidateName}
                  </Link>
                </TableCell>
                <TableCell className="max-w-64 align-top whitespace-normal">
                  <JobCell entry={entry} />
                </TableCell>
                <TableCell className="align-top">
                  <ApplicationStatusBadge status={entry.applicationStatus} />
                </TableCell>
                <TableCell className="align-top text-m3-on-surface-variant">{formatDateVi(entry.appliedAt)}</TableCell>
                <TableCell className="align-top text-m3-on-surface-variant">{formatDateVi(entry.addedAt)}</TableCell>
                <TableCell className="align-top">
                  <Actions entry={entry} onRemove={onRemove} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {/* < md: moi muc mot the outlined (UI.md 4c). */}
      <ul className="flex flex-col gap-3 md:hidden">
        {entries.map((entry) => (
          <li
            key={entry.applicationId}
            className="flex min-w-0 flex-col gap-2 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-4"
          >
            <Link
              to={applicationHref(entry)}
              className="break-words text-m3-title-md text-m3-primary hover:underline"
            >
              {entry.candidateName}
            </Link>
            <JobCell entry={entry} />
            <div>
              <ApplicationStatusBadge status={entry.applicationStatus} />
            </div>
            <p className="text-sm text-m3-on-surface-variant">
              {TALENT_POOL_TEXT.appliedOn(formatDateVi(entry.appliedAt))} ·{' '}
              {TALENT_POOL_TEXT.addedOn(formatDateVi(entry.addedAt))}
            </p>
            <Actions entry={entry} onRemove={onRemove} />
          </li>
        ))}
      </ul>
    </>
  )
}

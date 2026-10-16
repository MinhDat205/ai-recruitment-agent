import { AlertCircle } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { extractErrorMessage } from '../../lib/httpError'
import { useApplicationTalentPoolsQuery, useReplaceApplicationTalentPoolsMutation } from './queries'
import {
  TALENT_POOL_CONSENT_TEXT,
  TALENT_POOL_STATE_TEXT,
  TALENT_POOL_TEXT,
} from './talentPoolLabels'
import type { ApplicationTalentPools } from './types'

// FR-H15 UI.md 4d - hop thoai "Luu vao kho" o header trang ho so don. Mo thi goi P6; "Luu" gui P7 voi TOAN BO tap dang
// tick (R-P1). Don khong ACTIVE: P6 da loc dong y (R-M6) nen moi o contains = false -> moi o khoa, chi co nut "Dong"
// (UI.md 4d). Khong goi AI.
interface SaveToTalentPoolDialogProps {
  applicationId: string
  candidateName: string
  open: boolean
  onOpenChange: (open: boolean) => void
  // Goi sau khi P7 thanh cong, TRUOC khi dong hop thoai (header hien cau "Da cap nhat kho ung vien.").
  onSaved: () => void
}

export function SaveToTalentPoolDialog({
  applicationId,
  candidateName,
  open,
  onOpenChange,
  onSaved,
}: SaveToTalentPoolDialogProps) {
  const poolsQuery = useApplicationTalentPoolsQuery(applicationId, open)
  const data = poolsQuery.data
  // Chi dung du lieu da tai lai SAU lan mo nay - tranh hien lua chon cu trong luc P6 dang tai lai.
  const ready = data !== undefined && !poolsQuery.isFetching

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="break-words">{TALENT_POOL_TEXT.saveDialogTitle(candidateName)}</DialogTitle>
        </DialogHeader>

        {!ready && !poolsQuery.isError && <OptionsSkeleton />}

        {poolsQuery.isError && !poolsQuery.isFetching && (
          <div role="alert" className="flex flex-col items-start gap-3">
            <p className="flex items-center gap-2 text-sm text-m3-on-surface">
              <AlertCircle className="h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
              {TALENT_POOL_STATE_TEXT.listLoadFailed}
            </p>
            <Button type="button" variant="outline" className="min-h-12 sm:min-h-0" onClick={() => poolsQuery.refetch()}>
              {TALENT_POOL_TEXT.retry}
            </Button>
          </div>
        )}

        {/* key = lan tai: moi lan mo lai (P6 tai lai) thi lua chon khoi tao lai tu contains. */}
        {ready && !poolsQuery.isError && (
          <SaveForm
            key={poolsQuery.dataUpdatedAt}
            applicationId={applicationId}
            data={data}
            onCancel={() => onOpenChange(false)}
            onSaved={() => {
              onSaved()
              onOpenChange(false)
            }}
          />
        )}

        {/* Dang tai / loi van can mot nut dong ro rang. */}
        {(!ready || poolsQuery.isError) && (
          <DialogFooter>
            <Button type="button" variant="ghost" className="min-h-12 sm:min-h-0" onClick={() => onOpenChange(false)}>
              {TALENT_POOL_TEXT.close}
            </Button>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  )
}

function SaveForm({
  applicationId,
  data,
  onCancel,
  onSaved,
}: {
  applicationId: string
  data: ApplicationTalentPools
  onCancel: () => void
  onSaved: () => void
}) {
  const initial = data.pools.filter((pool) => pool.contains).map((pool) => pool.id)
  const [selected, setSelected] = useState<ReadonlySet<string>>(() => new Set(initial))
  const mutation = useReplaceApplicationTalentPoolsMutation(applicationId)
  const active = data.consentState === 'ACTIVE'
  const noticeId = `talent-pool-consent-${applicationId}`
  const changed = selected.size !== initial.length || initial.some((id) => !selected.has(id))

  function toggle(poolId: string, checked: boolean) {
    setSelected((current) => {
      const next = new Set(current)
      if (checked) {
        next.add(poolId)
      } else {
        next.delete(poolId)
      }
      return next
    })
  }

  function save() {
    // Giu thu tu theo danh sach kho (R-K7) cho de doc request; backend khong phu thuoc thu tu.
    const poolIds = data.pools.filter((pool) => selected.has(pool.id)).map((pool) => pool.id)
    mutation.mutate(poolIds, { onSuccess: onSaved })
  }

  if (data.pools.length === 0) {
    return (
      <>
        <div className="flex flex-col items-start gap-2">
          <p className="text-sm text-m3-on-surface-variant">{TALENT_POOL_STATE_TEXT.listEmpty}</p>
          <Link
            to="/hr/talent-pools"
            className="inline-flex min-h-12 items-center text-sm font-medium text-m3-primary hover:underline sm:min-h-0"
          >
            {TALENT_POOL_TEXT.createPool}
          </Link>
        </div>
        <DialogFooter>
          <Button type="button" variant="ghost" className="min-h-12 sm:min-h-0" onClick={onCancel}>
            {TALENT_POOL_TEXT.close}
          </Button>
        </DialogFooter>
      </>
    )
  }

  return (
    <>
      {!active && (
        // Khoi thong tin trung tinh (UI.md 5d): khong do/vang, khong icon canh bao.
        <p
          id={noticeId}
          className="rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface-container p-3 text-sm text-m3-on-surface"
        >
          {data.consentState === 'REVOKED' ? TALENT_POOL_CONSENT_TEXT.REVOKED : TALENT_POOL_CONSENT_TEXT.NOT_GIVEN}
        </p>
      )}

      <ul className="flex flex-col">
        {data.pools.map((pool) => {
          const checkboxId = `talent-pool-option-${applicationId}-${pool.id}`
          return (
            <li key={pool.id} className="flex min-h-12 items-center gap-3">
              <Checkbox
                id={checkboxId}
                checked={selected.has(pool.id)}
                disabled={!active || mutation.isPending}
                aria-describedby={!active ? noticeId : undefined}
                onCheckedChange={(checked) => toggle(pool.id, checked === true)}
              />
              <Label htmlFor={checkboxId} className="min-w-0 flex-1 break-words py-3 font-normal text-m3-on-surface">
                {pool.name}
              </Label>
            </li>
          )
        })}
      </ul>

      {mutation.isError && (
        <p role="alert" className="text-sm text-m3-error">
          {extractErrorMessage(mutation.error, TALENT_POOL_STATE_TEXT.actionFailed)}
        </p>
      )}

      <DialogFooter>
        {active ? (
          <>
            <Button
              type="button"
              variant="ghost"
              className="min-h-12 sm:min-h-0"
              disabled={mutation.isPending}
              onClick={onCancel}
            >
              {TALENT_POOL_TEXT.cancel}
            </Button>
            <Button type="button" className="min-h-12 sm:min-h-0" disabled={!changed || mutation.isPending} onClick={save}>
              {mutation.isPending ? TALENT_POOL_TEXT.saving : TALENT_POOL_TEXT.save}
            </Button>
          </>
        ) : (
          <Button type="button" variant="ghost" className="min-h-12 sm:min-h-0" onClick={onCancel}>
            {TALENT_POOL_TEXT.close}
          </Button>
        )}
      </DialogFooter>
    </>
  )
}

// Skeleton 2 dong o chon (UI.md muc 6).
function OptionsSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-busy="true" aria-hidden="true">
      {Array.from({ length: 2 }, (_, index) => (
        <div key={index} className="h-8 animate-pulse rounded-m3-xs bg-m3-surface-container motion-reduce:animate-none" />
      ))}
    </div>
  )
}

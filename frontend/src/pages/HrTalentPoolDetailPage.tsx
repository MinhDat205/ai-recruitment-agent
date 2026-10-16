import { useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { AlertCircle, ArrowLeft } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { HrLayout } from '../components/layout/HrLayout'
import { Pagination } from '../features/jobs/Pagination'
import { ConfirmTalentPoolDialog } from '../features/talentPools/ConfirmTalentPoolDialog'
import {
  TALENT_POOLS_KEY,
  talentPoolKey,
  useDeleteTalentPoolMutation,
  useRemoveTalentPoolEntryMutation,
  useTalentPoolEntriesQuery,
  useTalentPoolQuery,
} from '../features/talentPools/queries'
import { TalentPoolEntryList } from '../features/talentPools/TalentPoolEntryList'
import { TALENT_POOL_STATE_TEXT, TALENT_POOL_TEXT } from '../features/talentPools/talentPoolLabels'
import { TalentPoolNameDialog } from '../features/talentPools/TalentPoolNameDialog'
import type { TalentPool, TalentPoolEntry } from '../features/talentPools/types'
import { extractErrorMessage } from '../lib/httpError'

// FR-H15 UI.md 4b/4c - /hr/talent-pools/:id?page=N (page 0-based, giong P8). Tai P3 (dau trang) va P8 (danh sach muc)
// song song; P3 loi thi khong hien danh sach. 403 va 404 CUNG mot cau "Khong tim thay kho ung vien." (UI_GUIDE muc 5).

function parsePage(raw: string | null): number {
  const value = Number(raw)
  return Number.isInteger(value) && value > 0 ? value : 0
}

// 400 (id tren URL khong phai UUID), 403 (kho cong ty khac), 404 (khong ton tai / da xoa) -> cung "khong tim thay".
function isNotFound(error: unknown): boolean {
  if (!isAxiosError(error) || error.response === undefined) {
    return false
  }
  const status = error.response.status
  return status === 400 || status === 403 || status === 404
}

export function HrTalentPoolDetailPage() {
  const { id } = useParams<{ id: string }>()
  if (!id) {
    return null
  }
  // key = id: doi sang kho khac thi dung lai trang thai hop thoai tu dau.
  return <TalentPoolDetailContent key={id} poolId={id} />
}

function TalentPoolDetailContent({ poolId }: { poolId: string }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const page = parsePage(searchParams.get('page'))
  const poolQuery = useTalentPoolQuery(poolId)
  const entriesQuery = useTalentPoolEntriesQuery(poolId, page)
  const pool = poolQuery.data
  const entries = entriesQuery.data

  const [renameOpen, setRenameOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [removing, setRemoving] = useState<TalentPoolEntry | null>(null)
  const deleteMutation = useDeleteTalentPoolMutation(poolId)
  const removeMutation = useRemoveTalentPoolEntryMutation(poolId)
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  function setPage(next: number) {
    setSearchParams(
      (current) => {
        const params = new URLSearchParams(current)
        if (next > 0) {
          params.set('page', String(next))
        } else {
          params.delete('page')
        }
        return params
      },
      { replace: true },
    )
  }

  // UI.md 4e - trang dang xem rong sau khi go (khong phai trang 1) -> lui mot trang. Cung xu ly ?page= vuot qua so trang.
  useEffect(() => {
    if (entries && !entriesQuery.isFetching && entries.items.length === 0 && page > 0) {
      setPage(Math.max(0, Math.min(page - 1, entries.totalPages - 1)))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entries, entriesQuery.isFetching, page])

  function openRemove(entry: TalentPoolEntry) {
    removeMutation.reset()
    setRemoving(entry)
  }

  function confirmRemove() {
    if (!removing) return
    removeMutation.mutate(removing.applicationId, { onSuccess: () => setRemoving(null) })
  }

  function confirmDelete() {
    deleteMutation.mutate(undefined, {
      onSuccess: () => {
        // Bo cache cua kho vua xoa truoc khi lam moi danh sach - tranh tai lai P3/P8 cua kho da xoa (404).
        queryClient.removeQueries({ queryKey: talentPoolKey(poolId) })
        void queryClient.invalidateQueries({ queryKey: TALENT_POOLS_KEY })
        navigate('/hr/talent-pools', { state: { focusHeading: true } })
      },
    })
  }

  return (
    <HrLayout title={pool?.name ?? TALENT_POOL_TEXT.listTitle}>
      <div className="mx-auto flex w-full max-w-[1200px] min-w-0 flex-col gap-4">
        <Link
          to="/hr/talent-pools"
          className="inline-flex min-h-12 items-center gap-1.5 self-start text-sm text-m3-primary hover:underline sm:min-h-0"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          {TALENT_POOL_TEXT.backToList}
        </Link>

        {poolQuery.isLoading && <DetailSkeleton />}

        {!poolQuery.isLoading && !pool && (
          <LoadError
            notFound={isNotFound(poolQuery.error)}
            onRetry={() => {
              void poolQuery.refetch()
              void entriesQuery.refetch()
            }}
          />
        )}

        {pool && (
          <>
            <PoolHeader pool={pool} onRename={() => setRenameOpen(true)} onDelete={() => setDeleteOpen(true)} />

            {entriesQuery.isLoading && <DetailSkeleton rowsOnly />}

            {!entriesQuery.isLoading && !entries && (
              <LoadError notFound={isNotFound(entriesQuery.error)} onRetry={() => void entriesQuery.refetch()} />
            )}

            {entries && entries.items.length === 0 && page === 0 && (
              <p className="text-sm text-m3-on-surface">{TALENT_POOL_STATE_TEXT.detailEmpty}</p>
            )}

            {entries && entries.items.length > 0 && (
              <>
                <TalentPoolEntryList entries={entries.items} onRemove={openRemove} />
                <Pagination page={entries.page} totalPages={entries.totalPages} onPageChange={setPage} />
              </>
            )}

            <TalentPoolNameDialog open={renameOpen} onOpenChange={setRenameOpen} pool={pool} />

            <ConfirmTalentPoolDialog
              open={deleteOpen}
              onOpenChange={(open) => {
                if (open) deleteMutation.reset()
                setDeleteOpen(open)
              }}
              title={TALENT_POOL_TEXT.deleteDialogTitle}
              message={TALENT_POOL_TEXT.deleteConfirm(pool.name)}
              confirmLabel={TALENT_POOL_TEXT.deletePool}
              pendingLabel={TALENT_POOL_TEXT.deleting}
              pending={deleteMutation.isPending}
              error={
                deleteMutation.isError
                  ? extractErrorMessage(deleteMutation.error, TALENT_POOL_STATE_TEXT.actionFailed)
                  : null
              }
              onConfirm={confirmDelete}
            />

            <ConfirmTalentPoolDialog
              open={removing !== null}
              onOpenChange={(open) => {
                if (!open) setRemoving(null)
              }}
              title={TALENT_POOL_TEXT.removeDialogTitle}
              message={removing ? TALENT_POOL_TEXT.removeConfirm(removing.candidateName, pool.name) : ''}
              confirmLabel={TALENT_POOL_TEXT.remove}
              pendingLabel={TALENT_POOL_TEXT.removing}
              pending={removeMutation.isPending}
              error={
                removeMutation.isError
                  ? extractErrorMessage(removeMutation.error, TALENT_POOL_STATE_TEXT.actionFailed)
                  : null
              }
              onConfirm={confirmRemove}
            />
          </>
        )}
      </div>
    </HrLayout>
  )
}

// UI.md 4b - h1 ten kho, so ho so, hai nut Outlined. < sm: hai nut chia doi, cao 48px (4c).
function PoolHeader({ pool, onRename, onDelete }: { pool: TalentPool; onRename: () => void; onDelete: () => void }) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex min-w-0 flex-col gap-1">
        <h1 className="break-words text-m3-title-lg text-m3-on-surface">{pool.name}</h1>
        <p className="text-sm text-m3-on-surface-variant">{TALENT_POOL_TEXT.entryCount(pool.entryCount)}</p>
      </div>
      <div className="grid grid-cols-2 gap-2 sm:flex sm:shrink-0">
        <Button type="button" variant="outline" className="min-h-12 sm:min-h-0" onClick={onRename}>
          {TALENT_POOL_TEXT.rename}
        </Button>
        <Button type="button" variant="outline" className="min-h-12 sm:min-h-0" onClick={onDelete}>
          {TALENT_POOL_TEXT.deletePool}
        </Button>
      </div>
    </div>
  )
}

// Cau loi tren nen trang: chu on-surface, tin hieu loi bang icon m3-error (UI_GUIDE muc 6).
function LoadError({ notFound, onRetry }: { notFound: boolean; onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-start gap-3">
      <p className="flex items-center gap-2 text-sm text-m3-on-surface">
        <AlertCircle className="h-4 w-4 shrink-0 text-m3-error" aria-hidden="true" />
        {notFound ? TALENT_POOL_STATE_TEXT.notFound : TALENT_POOL_STATE_TEXT.loadFailed}
      </p>
      {notFound ? (
        <Link
          to="/hr/talent-pools"
          className="inline-flex min-h-12 items-center text-sm text-m3-primary hover:underline sm:min-h-0"
        >
          {TALENT_POOL_TEXT.backToListFromError}
        </Link>
      ) : (
        <Button type="button" variant="outline" className="min-h-12 sm:min-h-0" onClick={onRetry}>
          {TALENT_POOL_TEXT.retry}
        </Button>
      )}
    </div>
  )
}

// Skeleton tieu de + 3 dong (UI.md muc 6); rowsOnly khi dau trang da co.
function DetailSkeleton({ rowsOnly = false }: { rowsOnly?: boolean }) {
  return (
    <div className="flex flex-col gap-3" aria-busy="true" aria-hidden="true">
      {!rowsOnly && (
        <div className="h-8 w-1/2 animate-pulse rounded-m3-xs bg-m3-surface-container motion-reduce:animate-none" />
      )}
      {Array.from({ length: 3 }, (_, index) => (
        <div
          key={index}
          className="h-12 animate-pulse rounded-(--radius-card) bg-m3-surface-container motion-reduce:animate-none"
        />
      ))}
    </div>
  )
}

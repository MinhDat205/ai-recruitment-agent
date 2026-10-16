import { AlertCircle, Plus } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { HrLayout } from '../components/layout/HrLayout'
import { TALENT_POOL_STATE_TEXT, TALENT_POOL_TEXT } from '../features/talentPools/talentPoolLabels'
import { TalentPoolNameDialog } from '../features/talentPools/TalentPoolNameDialog'
import { useTalentPoolsQuery } from '../features/talentPools/queries'

// FR-H15 UI.md 4a - /hr/talent-pools (trong nhom ProtectedRoute(HR) + RequireCompany). Danh sach la mot Card chua cac
// dong (List MD3), sap theo ten o backend (R-K7), khong phan trang, khong gioi han so kho (R-K6). Doi ten / xoa o trang
// chi tiet. Vua xoa kho xong (state.focusHeading tu trang chi tiet) -> focus ve h1 (UI.md muc 9).
export function HrTalentPoolListPage() {
  const poolsQuery = useTalentPoolsQuery()
  const [createOpen, setCreateOpen] = useState(false)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const location = useLocation()
  const focusHeading = (location.state as { focusHeading?: boolean } | null)?.focusHeading === true

  useEffect(() => {
    if (focusHeading) {
      headingRef.current?.focus()
    }
  }, [focusHeading])

  const pools = poolsQuery.data

  return (
    <HrLayout title={TALENT_POOL_TEXT.listTitle}>
      <div className="mx-auto flex w-full max-w-[1200px] min-w-0 flex-col gap-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <h1
            ref={headingRef}
            tabIndex={-1}
            className="text-m3-title-lg text-m3-on-surface outline-none focus-visible:ring-2 focus-visible:ring-m3-primary"
          >
            {TALENT_POOL_TEXT.listTitle}
          </h1>
          {/* L7 - nut o header chi khi da co it nhat 1 kho; rong thi chi nut trong trang thai rong; dang tai / loi: khong. */}
          {pools && pools.length > 0 && (
            <Button type="button" className="min-h-12 w-full sm:min-h-0 sm:w-auto" onClick={() => setCreateOpen(true)}>
              <Plus aria-hidden="true" />
              {TALENT_POOL_TEXT.createPool}
            </Button>
          )}
        </div>

        {poolsQuery.isLoading && <ListSkeleton />}

        {!poolsQuery.isLoading && !pools && (
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

        {pools && pools.length === 0 && (
          <div className="flex flex-col items-start gap-3">
            <p className="text-sm text-m3-on-surface">{TALENT_POOL_STATE_TEXT.listEmpty}</p>
            <Button type="button" className="min-h-12 w-full sm:min-h-0 sm:w-auto" onClick={() => setCreateOpen(true)}>
              <Plus aria-hidden="true" />
              {TALENT_POOL_TEXT.createPool}
            </Button>
          </div>
        )}

        {pools && pools.length > 0 && (
          <ul className="divide-y divide-m3-outline-variant overflow-hidden rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface">
            {pools.map((pool) => (
              <li key={pool.id}>
                <Link
                  to={`/hr/talent-pools/${pool.id}`}
                  className="flex min-h-12 items-center justify-between gap-3 px-4 py-3 hover:bg-m3-surface-container focus-visible:ring-2 focus-visible:ring-m3-primary focus-visible:outline-none focus-visible:ring-inset"
                >
                  <span className="min-w-0 break-words text-m3-on-surface">{pool.name}</span>
                  <span className="shrink-0 text-sm text-m3-on-surface-variant">
                    {TALENT_POOL_TEXT.entryCount(pool.entryCount)}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      <TalentPoolNameDialog open={createOpen} onOpenChange={setCreateOpen} />
    </HrLayout>
  )
}

// Skeleton xam 3 dong trong Card (UI.md muc 6), khong spinner toan trang.
function ListSkeleton() {
  return (
    <div
      className="flex flex-col gap-3 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-4"
      aria-busy="true"
      aria-hidden="true"
    >
      {Array.from({ length: 3 }, (_, index) => (
        <div key={index} className="h-6 animate-pulse rounded-m3-xs bg-m3-surface-container motion-reduce:animate-none" />
      ))}
    </div>
  )
}

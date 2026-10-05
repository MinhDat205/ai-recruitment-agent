export function JobCardSkeleton() {
  return (
    <div className="flex animate-pulse gap-4 rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-4">
      <div className="h-20 w-20 shrink-0 rounded-(--radius-badge) bg-m3-surface-container" />
      <div className="flex flex-1 flex-col gap-2">
        <div className="h-4 w-3/4 rounded bg-m3-surface-container" />
        <div className="h-3 w-1/2 rounded bg-m3-surface-container" />
        <div className="h-3 w-1/3 rounded bg-m3-surface-container" />
      </div>
    </div>
  )
}

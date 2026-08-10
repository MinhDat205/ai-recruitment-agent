export function JobCardSkeleton() {
  return (
    <div className="flex animate-pulse gap-4 rounded-(--radius-card) border border-line bg-surface p-4">
      <div className="h-20 w-20 shrink-0 rounded-(--radius-badge) bg-canvas" />
      <div className="flex flex-1 flex-col gap-2">
        <div className="h-4 w-3/4 rounded bg-canvas" />
        <div className="h-3 w-1/2 rounded bg-canvas" />
        <div className="h-3 w-1/3 rounded bg-canvas" />
      </div>
    </div>
  )
}

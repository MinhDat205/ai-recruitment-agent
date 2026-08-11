import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../../features/auth/useAuth'

interface NavItem {
  label: string
  to?: string
}

// Dung 5 muc theo docs/UI_GUIDE.md muc 3. Chi "Ho so cong ty" co route that o B1 - 4 muc con
// lai la placeholder khong the bam, cho tan cac nhanh tuong ung (B2, B3...) lam.
const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard' },
  { label: 'Tin tuyển dụng' },
  { label: 'Ứng viên' },
  { label: 'Rubric' },
  { label: 'Hồ sơ công ty', to: '/hr/company' },
]

export function HrLayout({ title, children }: { title: string; children: ReactNode }) {
  const { user, logout } = useAuth()
  const location = useLocation()

  return (
    <div className="flex min-h-screen">
      <aside className="flex w-60 shrink-0 flex-col border-r border-line bg-surface">
        <Link
          to="/hr"
          className="flex h-16 items-center border-b border-line px-6 text-lg font-semibold text-brand"
        >
          AI Recruitment
        </Link>
        <nav className="flex flex-col gap-1 p-3">
          {NAV_ITEMS.map((item) => {
            if (!item.to) {
              return (
                <span
                  key={item.label}
                  aria-disabled="true"
                  className="cursor-not-allowed rounded-md px-3 py-2 text-sm text-ink-muted/60"
                >
                  {item.label}
                </span>
              )
            }
            const active = location.pathname === item.to
            return (
              <Link
                key={item.label}
                to={item.to}
                className={`rounded-md px-3 py-2 text-sm font-medium ${
                  active ? 'bg-brand-light text-brand' : 'text-ink hover:bg-canvas'
                }`}
              >
                {item.label}
              </Link>
            )
          })}
        </nav>
      </aside>

      <div className="flex flex-1 flex-col">
        <header className="flex h-16 items-center justify-between border-b border-line bg-surface px-6">
          <p className="text-sm text-ink-muted">Quản trị / {title}</p>
          <div className="flex items-center gap-3">
            {user?.avatarUrl ? (
              <img src={user.avatarUrl} alt={user.fullName} className="h-8 w-8 rounded-full object-cover" />
            ) : (
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-light text-sm font-medium text-brand">
                {user?.fullName?.charAt(0).toUpperCase()}
              </div>
            )}
            <span className="text-sm text-ink">{user?.fullName}</span>
            <button
              type="button"
              onClick={logout}
              className="h-8 rounded-md border border-line px-3 text-sm text-ink-muted hover:text-ink"
            >
              Đăng xuất
            </button>
          </div>
        </header>
        <main className="flex-1 p-8">{children}</main>
      </div>
    </div>
  )
}

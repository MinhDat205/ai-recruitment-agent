import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../../features/auth/useAuth'
import { NotificationBell } from '../../features/notifications/NotificationBell'

interface NavItem {
  label: string
  to?: string
}

// Dung 5 muc theo docs/UI_GUIDE.md muc 3. "Ho so cong ty" co route tu B1, "Tin tuyen dung" co
// route tu B2, "Dashboard" co route tu F3/Dot 5 - "Ung vien" con lai la placeholder, cho Dot 6
// (F3) lam. "Rubric" se bi xoa hoan toan khoi mang nay o Dot 6 (rubric thuoc tung job, da co tab
// rieng trong HrJobEditPage - de o menu cap cao la dieu huong cut, xem quyet dinh #10 trong plan).
const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', to: '/hr' },
  { label: 'Tin tuyển dụng', to: '/hr/jobs' },
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
            // "/hr/jobs" phai active ca o cac trang con (/hr/jobs/new, /hr/jobs/:id/edit) - nhung
            // "/hr" (Dashboard) la tien to cua MOI route HR khac, nen KHONG duoc dung prefix match
            // cho no (startsWith('/hr/') se khop nham voi ca /hr/jobs, /hr/company...), chi active
            // dung khi khop chinh xac.
            const active =
              location.pathname === item.to ||
              (item.to !== '/hr' && location.pathname.startsWith(`${item.to}/`))
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
            <NotificationBell />
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

import type { ReactNode } from 'react'
import { Briefcase, Building2, LayoutDashboard, Users, type LucideIcon } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../../features/auth/useAuth'
import { NotificationBell } from '../../features/notifications/NotificationBell'

interface NavItem {
  label: string
  // Nhan ngan cho navigation rail (< lg) - rail chi rong 80px
  shortLabel: string
  to: string
  icon: LucideIcon
}

// docs/UI_GUIDE.md muc 3 liet ke 5 muc (bao gom "Rubric") nhung da lac hau so voi quyet dinh #10
// trong plan FR-H08: BO HAN "Rubric" khoi menu cap cao - rubric thuoc TUNG job, da co tab rieng
// trong HrJobEditPage, dat o menu cap cao la dieu huong cut (khong co trang "Rubric" doc lap nao
// de tro toi). Con lai 4 muc, tat ca da co route: "Ho so cong ty" (B1), "Tin tuyen dung" (B2),
// "Dashboard" (F3/Dot 5), "Ung vien" (F3/Dot 6).
const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', shortLabel: 'Dashboard', to: '/hr', icon: LayoutDashboard },
  { label: 'Tin tuyển dụng', shortLabel: 'Tin', to: '/hr/jobs', icon: Briefcase },
  { label: 'Ứng viên', shortLabel: 'Ứng viên', to: '/hr/candidates', icon: Users },
  { label: 'Hồ sơ công ty', shortLabel: 'Công ty', to: '/hr/company', icon: Building2 },
]

// UI_GUIDE muc 2: navigation drawer 240px khi >= lg, navigation rail (icon + nhan ngan) khi < lg.
// Chi doi bang class responsive, khong co state dong/mo.
export function HrLayout({ title, children }: { title: string; children: ReactNode }) {
  const { user, logout } = useAuth()
  const location = useLocation()

  return (
    <div className="flex min-h-screen">
      <aside className="flex w-20 shrink-0 flex-col border-r border-m3-outline-variant bg-m3-surface lg:w-60">
        <Link
          to="/hr"
          className="flex h-16 items-center justify-center border-b border-m3-outline-variant text-lg font-semibold text-m3-primary lg:justify-start lg:px-6"
        >
          <Briefcase size={22} className="lg:hidden" aria-hidden="true" />
          <span className="sr-only lg:not-sr-only">AI Recruitment</span>
        </Link>
        <nav className="flex flex-col gap-1 px-1 py-3 lg:p-3">
          {NAV_ITEMS.map((item) => {
            // "/hr/jobs" phai active ca o cac trang con (/hr/jobs/new, /hr/jobs/:id/edit) - nhung
            // "/hr" (Dashboard) la tien to cua MOI route HR khac, nen KHONG duoc dung prefix match
            // cho no (startsWith('/hr/') se khop nham voi ca /hr/jobs, /hr/company...), chi active
            // dung khi khop chinh xac.
            const active =
              location.pathname === item.to ||
              (item.to !== '/hr' && location.pathname.startsWith(`${item.to}/`))
            const Icon = item.icon
            return (
              <Link
                key={item.label}
                to={item.to}
                aria-current={active ? 'page' : undefined}
                className={`flex flex-col items-center gap-1 rounded-md px-1 py-2 text-center text-xs font-medium lg:flex-row lg:gap-3 lg:px-3 lg:text-left lg:text-sm ${
                  active
                    ? 'bg-m3-primary-container text-m3-on-primary-container'
                    : 'text-m3-on-surface hover:bg-m3-surface-container'
                }`}
              >
                <Icon size={20} className="shrink-0" aria-hidden="true" />
                <span className="lg:hidden">{item.shortLabel}</span>
                <span className="hidden lg:inline">{item.label}</span>
              </Link>
            )
          })}
        </nav>
      </aside>

      <div className="flex flex-1 flex-col">
        <header className="flex h-16 items-center justify-between gap-3 border-b border-m3-outline-variant bg-m3-surface px-4 lg:px-6">
          <p className="min-w-0 truncate text-sm text-m3-on-surface-variant">Quản trị / {title}</p>
          <div className="flex shrink-0 items-center gap-3">
            <NotificationBell />
            {user?.avatarUrl ? (
              <img src={user.avatarUrl} alt={user.fullName} className="h-8 w-8 rounded-full object-cover" />
            ) : (
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-m3-primary-container text-sm font-medium text-m3-on-primary-container">
                {user?.fullName?.charAt(0).toUpperCase()}
              </div>
            )}
            <span className="hidden text-sm text-m3-on-surface sm:inline">{user?.fullName}</span>
            <button
              type="button"
              onClick={logout}
              className="h-8 rounded-md border border-m3-outline px-3 text-sm text-m3-on-surface-variant hover:text-m3-on-surface"
            >
              Đăng xuất
            </button>
          </div>
        </header>
        <main className="flex-1 p-4 sm:p-6 lg:p-8">{children}</main>
      </div>
    </div>
  )
}

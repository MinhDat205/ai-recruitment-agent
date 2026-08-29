import type { ReactNode } from 'react'
import { Briefcase } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../../features/auth/useAuth'
import { NotificationBell } from '../../features/notifications/NotificationBell'

interface NavItem {
  label: string
  to: string
  isActive: (pathname: string) => boolean
}

// Moi muc tu quyet dinh isActive rieng - KHONG dung chung mot quy tac exact/tien to cho
// tat ca: "/" (Viec lam) khong the dung tien to (moi route deu bat dau bang "/", se sang
// nham moi trang), va "/candidate" (Bang tin) khong the dung tien to (se sang nham ca
// /candidate/profile, /candidate/applications...). "Viec lam" sang o ca trang chi tiet
// job (/jobs/:id) vi do van la luong duyet job, tro ve dung route danh sach cong khai that
// (A2/FR-C02: PublicJobListPage o "/", co HeroSearch + JobList - da xac nhan doc code, khong
// phai trang chu marketing rieng).
const NAV_ITEMS: NavItem[] = [
  { label: 'Bảng tin', to: '/candidate', isActive: (path) => path === '/candidate' },
  {
    label: 'Việc làm',
    to: '/',
    isActive: (path) => path === '/' || path === '/jobs' || path.startsWith('/jobs/'),
  },
  {
    label: 'Hồ sơ và CV',
    to: '/candidate/profile',
    isActive: (path) => path.startsWith('/candidate/profile'),
  },
  {
    label: 'Đơn ứng tuyển',
    to: '/candidate/applications',
    isActive: (path) => path.startsWith('/candidate/applications'),
  },
]

// Thong bao (E2/FR-C03) khong co muc nav rieng - chuong NotificationBell da la loi vao
// (NotificationDropdown.notificationsPagePath tro dung /candidate/notifications cho role
// CANDIDATE, ca o link "Xem tat ca" lan tung thong bao khong co item.link rieng).
export function CandidateLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const location = useLocation()

  function navLinkClass(active: boolean): string {
    return `shrink-0 whitespace-nowrap border-b-2 py-2 text-sm font-medium sm:py-5 ${
      active ? 'border-brand text-brand' : 'border-transparent text-ink hover:text-brand'
    }`
  }

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-10 border-b border-line bg-surface">
        <div className="mx-auto flex h-16 max-w-[1200px] items-center justify-between px-4 md:px-6">
          <Link to="/candidate" className="flex items-center gap-2 text-lg font-semibold text-ink">
            <Briefcase size={22} className="text-brand" aria-hidden="true" />
            AI Recruitment Agent
          </Link>

          <nav className="hidden items-center gap-6 sm:flex">
            {NAV_ITEMS.map((item) => (
              <Link key={item.label} to={item.to} className={navLinkClass(item.isActive(location.pathname))}>
                {item.label}
              </Link>
            ))}
          </nav>

          <div className="flex items-center gap-3">
            <NotificationBell />
            {user?.avatarUrl ? (
              <img src={user.avatarUrl} alt={user.fullName} className="h-8 w-8 rounded-full object-cover" />
            ) : (
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-light text-sm font-medium text-brand">
                {user?.fullName?.charAt(0).toUpperCase()}
              </div>
            )}
            <span className="hidden text-sm text-ink md:inline">{user?.fullName}</span>
            <button
              type="button"
              onClick={logout}
              className="h-8 rounded-md border border-line px-3 text-sm text-ink-muted hover:text-ink"
            >
              Đăng xuất
            </button>
          </div>
        </div>

        {/* Hang nav thu hai, chi hien tren mobile (sm:hidden) - hang chinh o tren an nav tren
            mobile (hidden sm:flex) nen day la duong dieu huong DUY NHAT tren man hinh nho, khong
            phai lop trang tri them. Cuon ngang thay vi xuong dong de khong doi chieu cao header. */}
        <nav className="flex gap-5 overflow-x-auto border-t border-line px-4 sm:hidden">
          {NAV_ITEMS.map((item) => (
            <Link key={item.label} to={item.to} className={navLinkClass(item.isActive(location.pathname))}>
              {item.label}
            </Link>
          ))}
        </nav>
      </header>
      <main className="flex-1">{children}</main>
    </div>
  )
}

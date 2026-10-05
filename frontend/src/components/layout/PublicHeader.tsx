import { Briefcase } from 'lucide-react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { useAuth } from '../../features/auth/useAuth'
import type { Role } from '../../features/auth/types'
import { NotificationBell } from '../../features/notifications/NotificationBell'
import { MobileNavButton, MobileNavLink, MobileNavSheet } from './MobileNavSheet'

function roleHomePath(role: Role): string {
  if (role === 'CANDIDATE') return '/candidate'
  if (role === 'HR') return '/hr'
  return '/'
}

export function PublicHeader() {
  const { user, isLoading, logout } = useAuth()
  const location = useLocation()

  return (
    <header className="sticky top-0 z-10 border-b border-m3-outline-variant bg-m3-surface">
      <div className="mx-auto flex h-16 max-w-[1200px] items-center justify-between px-4 md:px-6">
        <div className="flex items-center gap-6">
          <Link to="/" className="flex items-center gap-2 text-lg font-semibold text-m3-on-surface">
            <Briefcase size={22} className="text-m3-primary" />
            AI Recruitment Agent
          </Link>

          <nav className="hidden items-center gap-6 text-sm text-m3-on-surface sm:flex">
            <NavLink
              to="/"
              end
              className={({ isActive }) =>
                `flex h-10 items-center rounded-md px-4 text-sm font-medium ${
                  isActive
                    ? 'bg-m3-primary-container text-m3-on-primary-container'
                    : 'bg-m3-surface-container text-m3-on-surface hover:bg-m3-primary-container hover:text-m3-on-primary-container'
                }`
              }
            >
              Việc làm
            </NavLink>
          </nav>
        </div>

        <div className="flex items-center gap-3">
          {isLoading ? null : user ? (
            <>
              <NotificationBell />
              <span className="hidden text-sm font-medium text-m3-on-surface sm:inline">{user.fullName}</span>
              <Link
                to={roleHomePath(user.role)}
                className="hidden h-10 items-center rounded-md border border-m3-primary px-4 text-sm font-medium text-m3-primary sm:flex"
              >
                Trang của tôi
              </Link>
              <button
                type="button"
                onClick={logout}
                className="hidden h-10 items-center rounded-md bg-m3-primary px-4 text-sm font-medium text-m3-on-primary sm:flex"
              >
                Đăng xuất
              </button>
            </>
          ) : (
            <>
              <Link
                to="/login"
                className="hidden h-10 items-center rounded-md border border-m3-primary px-4 text-sm font-medium text-m3-primary sm:flex"
              >
                Đăng nhập
              </Link>
              <Link
                to="/register"
                className="hidden h-10 items-center rounded-md bg-m3-primary px-4 text-sm font-medium text-m3-on-primary sm:flex"
              >
                Đăng ký
              </Link>
            </>
          )}

          {/* Duoi sm: nav va nut tai khoan o tren bi an, menu sheet la duong vao duy nhat. */}
          <MobileNavSheet triggerClassName="sm:hidden">
            <MobileNavLink to="/" active={location.pathname === '/'}>
              Việc làm
            </MobileNavLink>
            {isLoading ? null : user ? (
              <>
                <p className="mt-2 border-t border-m3-outline-variant px-4 pt-4 pb-2 text-sm font-medium text-m3-on-surface">
                  {user.fullName}
                </p>
                <MobileNavLink to={roleHomePath(user.role)}>Trang của tôi</MobileNavLink>
                <MobileNavButton onClick={logout}>Đăng xuất</MobileNavButton>
              </>
            ) : (
              <>
                <div className="mt-2 border-t border-m3-outline-variant pt-2" />
                <MobileNavLink to="/login">Đăng nhập</MobileNavLink>
                <MobileNavLink to="/register">Đăng ký</MobileNavLink>
              </>
            )}
          </MobileNavSheet>
        </div>
      </div>
    </header>
  )
}

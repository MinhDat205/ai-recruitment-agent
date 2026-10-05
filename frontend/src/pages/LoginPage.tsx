import { Link, useLocation } from 'react-router-dom'
import { LoginForm } from '../features/auth/LoginForm'

export function LoginPage() {
  const location = useLocation()
  const justRegistered = Boolean((location.state as { justRegistered?: boolean } | null)?.justRegistered)

  return (
    <div className="flex min-h-screen items-center justify-center bg-m3-surface-container px-4">
      <div className="w-full max-w-sm rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-8 shadow-sm">
        <h1 className="text-2xl font-semibold text-m3-on-surface">Đăng nhập</h1>
        <p className="mt-1 text-sm text-m3-on-surface-variant">Chào mừng bạn quay lại AI Recruitment Agent.</p>

        <div className="mt-6">
          <LoginForm showRegisteredNotice={justRegistered} />
        </div>

        <p className="mt-6 text-center text-sm text-m3-on-surface-variant">
          Chưa có tài khoản?{' '}
          <Link to="/register" className="font-medium text-m3-primary">
            Đăng ký
          </Link>
        </p>
      </div>
    </div>
  )
}

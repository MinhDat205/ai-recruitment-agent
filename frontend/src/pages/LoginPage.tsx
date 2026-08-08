import { Link, useLocation } from 'react-router-dom'
import { LoginForm } from '../features/auth/LoginForm'

export function LoginPage() {
  const location = useLocation()
  const justRegistered = Boolean((location.state as { justRegistered?: boolean } | null)?.justRegistered)

  return (
    <div className="flex min-h-screen items-center justify-center bg-canvas px-4">
      <div className="w-full max-w-sm rounded-[--radius-card] border border-line bg-surface p-8 shadow-sm">
        <h1 className="text-2xl font-semibold text-ink">Đăng nhập</h1>
        <p className="mt-1 text-sm text-ink-muted">Chào mừng bạn quay lại AI Recruitment Agent.</p>

        {justRegistered && (
          <p className="mt-4 rounded-[--radius-badge] bg-brand-light px-3 py-2 text-sm text-brand">
            Đăng ký thành công, vui lòng đăng nhập.
          </p>
        )}

        <div className="mt-6">
          <LoginForm />
        </div>

        <p className="mt-6 text-center text-sm text-ink-muted">
          Chưa có tài khoản?{' '}
          <Link to="/register" className="font-medium text-brand">
            Đăng ký
          </Link>
        </p>
      </div>
    </div>
  )
}

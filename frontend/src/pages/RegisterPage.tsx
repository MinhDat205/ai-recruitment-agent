import { Link } from 'react-router-dom'
import { RegisterForm } from '../features/auth/RegisterForm'

export function RegisterPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-canvas px-4 py-8">
      <div className="w-full max-w-sm rounded-[--radius-card] border border-line bg-surface p-8 shadow-sm">
        <h1 className="text-2xl font-semibold text-ink">Đăng ký</h1>
        <p className="mt-1 text-sm text-ink-muted">Tạo tài khoản để bắt đầu sử dụng.</p>

        <div className="mt-6">
          <RegisterForm />
        </div>

        <p className="mt-6 text-center text-sm text-ink-muted">
          Đã có tài khoản?{' '}
          <Link to="/login" className="font-medium text-brand">
            Đăng nhập
          </Link>
        </p>
      </div>
    </div>
  )
}

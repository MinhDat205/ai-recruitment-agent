import { Link } from 'react-router-dom'
import { RegisterForm } from '../features/auth/RegisterForm'

export function RegisterPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-m3-surface-container px-4 py-8">
      <div className="w-full max-w-sm rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-8 shadow-sm">
        <h1 className="text-2xl font-semibold text-m3-on-surface">Đăng ký</h1>
        <p className="mt-1 text-sm text-m3-on-surface-variant">Tạo tài khoản để bắt đầu sử dụng.</p>

        <div className="mt-6">
          <RegisterForm />
        </div>

        <p className="mt-6 text-center text-sm text-m3-on-surface-variant">
          Đã có tài khoản?{' '}
          <Link to="/login" className="font-medium text-m3-primary">
            Đăng nhập
          </Link>
        </p>
      </div>
    </div>
  )
}

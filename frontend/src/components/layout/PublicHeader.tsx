import { Briefcase } from 'lucide-react'
import { Link } from 'react-router-dom'

export function PublicHeader() {
  return (
    <header className="sticky top-0 z-10 border-b border-line bg-surface">
      <div className="mx-auto flex h-16 max-w-[1200px] items-center justify-between px-4 md:px-6">
        <Link to="/" className="flex items-center gap-2 text-lg font-semibold text-ink">
          <Briefcase size={22} className="text-brand" />
          AI Recruitment Agent
        </Link>

        <nav className="hidden items-center gap-6 text-sm text-ink sm:flex">
          <Link to="/" className="hover:text-brand">
            Việc làm
          </Link>
        </nav>

        <div className="flex items-center gap-3">
          <Link
            to="/login"
            className="flex h-10 items-center rounded-md border border-brand px-4 text-sm font-medium text-brand"
          >
            Đăng nhập
          </Link>
          <Link
            to="/register"
            className="flex h-10 items-center rounded-md bg-brand px-4 text-sm font-medium text-white"
          >
            Đăng ký
          </Link>
        </div>
      </div>
    </header>
  )
}

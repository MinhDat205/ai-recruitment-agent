import type { ReactNode } from 'react'
import { PublicFooter } from './PublicFooter'
import { PublicHeader } from './PublicHeader'

// Khong bo max-width o day: cac section (vd HeroSearch) can nen full-bleed va tu quan ly
// container rieng ben trong. Trang con lai tu boc bang class Tailwind truc tiep
// (vd "mx-auto max-w-[1200px] px-4 py-8 md:px-6"), khong co class dung chung nao.
export function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col">
      <PublicHeader />
      <main className="flex-1">{children}</main>
      <PublicFooter />
    </div>
  )
}

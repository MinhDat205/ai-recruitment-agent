import type { ReactNode } from 'react'
import { Menu } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Sheet, SheetBody, SheetClose, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'

// Menu dieu huong dang sheet cho man hinh hep (UI_GUIDE muc 2) - dung chung cho PublicHeader (< sm)
// va CandidateLayout (< md). Lop an/hien theo breakpoint do noi goi truyen qua triggerClassName.
export function MobileNavSheet({ triggerClassName, children }: { triggerClassName?: string; children: ReactNode }) {
  return (
    <Sheet>
      <SheetTrigger asChild>
        <button
          type="button"
          aria-label="Mở menu điều hướng"
          className={`flex size-12 shrink-0 items-center justify-center rounded-m3-button text-m3-on-surface hover:bg-m3-on-surface/8 ${triggerClassName ?? ''}`}
        >
          <Menu size={24} aria-hidden="true" />
        </button>
      </SheetTrigger>
      <SheetContent className="w-72 max-w-[85vw]" aria-describedby={undefined}>
        <SheetHeader>
          <SheetTitle>Menu</SheetTitle>
        </SheetHeader>
        <SheetBody className="flex flex-col gap-1 p-3">{children}</SheetBody>
      </SheetContent>
    </Sheet>
  )
}

// Muc dieu huong trong sheet - boc SheetClose de sheet tu dong lai khi chon muc. Vung cham >= 48px.
export function MobileNavLink({ to, active = false, children }: { to: string; active?: boolean; children: ReactNode }) {
  return (
    <SheetClose asChild>
      <Link
        to={to}
        aria-current={active ? 'page' : undefined}
        className={`flex min-h-12 items-center rounded-m3-button px-4 text-sm font-medium ${
          active ? 'bg-m3-primary-container text-m3-on-primary-container' : 'text-m3-on-surface hover:bg-m3-on-surface/8'
        }`}
      >
        {children}
      </Link>
    </SheetClose>
  )
}

// Nut thao tac trong sheet (vd Dang xuat) - cung kieu MobileNavLink, dong sheet roi moi chay onClick.
export function MobileNavButton({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <SheetClose asChild>
      <button
        type="button"
        onClick={onClick}
        className="flex min-h-12 items-center rounded-m3-button px-4 text-left text-sm font-medium text-m3-on-surface hover:bg-m3-on-surface/8"
      >
        {children}
      </button>
    </SheetClose>
  )
}

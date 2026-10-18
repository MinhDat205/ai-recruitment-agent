import type { ComponentProps } from 'react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { CV_BUILDER_DATE_PLACEHOLDER } from './cvBuilderLabels'

// FR-U12 UI.md muc 5a - o moc thoi gian (R-F6: MM/YYYY hoac YYYY). O chu thuong, KHONG dung input type="month" (Safari
// desktop khong ho tro - UI.md muc 10), khong date picker. Nhan, goi y va loi do CvBuilderFormView dung quanh o nay.
export function MonthField({ className, ...props }: ComponentProps<'input'>) {
  return (
    <Input
      type="text"
      inputMode="numeric"
      autoComplete="off"
      placeholder={CV_BUILDER_DATE_PLACEHOLDER}
      className={cn('min-h-10', className)}
      {...props}
    />
  )
}

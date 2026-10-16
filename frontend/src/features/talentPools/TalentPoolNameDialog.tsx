import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { extractErrorMessage } from '../../lib/httpError'
import { useCreateTalentPoolMutation, useRenameTalentPoolMutation } from './queries'
import {
  TALENT_POOL_NAME_MAX,
  TALENT_POOL_STATE_TEXT,
  TALENT_POOL_TEXT,
  countNameChars,
} from './talentPoolLabels'
import type { TalentPool } from './types'

// FR-H15 UI.md 4e - hop thoai "Tao kho" / "Doi ten kho". Zod chi bao som (trim + dem code point, khop R-K2); backend la
// nguon dung: loi 400/409 hien duoi o nhap, hop thoai KHONG dong.
const nameSchema = z.object({
  name: z.string().refine((value) => {
    const length = countNameChars(value)
    return length >= 1 && length <= TALENT_POOL_NAME_MAX
  }, TALENT_POOL_STATE_TEXT.nameInvalid),
})

type NameFormValues = z.infer<typeof nameSchema>

interface TalentPoolNameDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  // Khong truyen pool = tao moi; truyen pool = doi ten (dien san ten hien tai).
  pool?: TalentPool
}

export function TalentPoolNameDialog({ open, onOpenChange, pool }: TalentPoolNameDialogProps) {
  const isRename = Boolean(pool)
  const createMutation = useCreateTalentPoolMutation()
  const renameMutation = useRenameTalentPoolMutation(pool?.id ?? '')
  const mutation = isRename ? renameMutation : createMutation
  const idPrefix = pool ? `talent-pool-${pool.id}` : 'talent-pool-new'

  const {
    register,
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<NameFormValues>({ resolver: zodResolver(nameSchema), defaultValues: { name: '' } })
  const name = useWatch({ control, name: 'name' }) ?? ''
  const length = countNameChars(name)
  const lengthValid = length >= 1 && length <= TALENT_POOL_NAME_MAX

  // Moi lan mo: nap lai gia tri va xoa loi backend cua lan truoc.
  useEffect(() => {
    if (!open) return
    reset({ name: pool?.name ?? '' })
    createMutation.reset()
    renameMutation.reset()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, pool])

  const onSubmit = handleSubmit(async (values) => {
    await mutation.mutateAsync(values.name.trim())
    onOpenChange(false)
  })

  const inputId = `${idPrefix}-name`
  const counterId = `${idPrefix}-counter`
  const errorId = `${idPrefix}-error`
  const errorMessage =
    errors.name?.message ??
    (mutation.isError ? extractErrorMessage(mutation.error, TALENT_POOL_STATE_TEXT.actionFailed) : null)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isRename ? TALENT_POOL_TEXT.renameDialogTitle : TALENT_POOL_TEXT.createDialogTitle}</DialogTitle>
        </DialogHeader>
        <form
          onSubmit={(event) => {
            // Loi da hien duoi o nhap qua mutation.isError - khong de promise bi tu choi lot ra ngoai.
            onSubmit(event).catch(() => undefined)
          }}
          noValidate
          className="flex min-w-0 flex-col gap-4"
        >
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={inputId}>{TALENT_POOL_TEXT.nameField}</Label>
            <Input
              id={inputId}
              autoComplete="off"
              className="min-h-12 sm:min-h-0"
              aria-invalid={errorMessage ? true : undefined}
              aria-describedby={errorMessage ? `${counterId} ${errorId}` : counterId}
              {...register('name')}
            />
            <p id={counterId} className="self-end text-sm text-m3-on-surface-variant">
              {TALENT_POOL_TEXT.charCounter(length)}
            </p>
            {errorMessage && (
              <p id={errorId} role="alert" className="text-sm text-m3-error">
                {errorMessage}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="ghost"
              className="min-h-12 sm:min-h-0"
              onClick={() => onOpenChange(false)}
            >
              {TALENT_POOL_TEXT.cancel}
            </Button>
            <Button type="submit" className="min-h-12 sm:min-h-0" disabled={!lengthValid || mutation.isPending}>
              {mutation.isPending ? TALENT_POOL_TEXT.saving : TALENT_POOL_TEXT.save}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

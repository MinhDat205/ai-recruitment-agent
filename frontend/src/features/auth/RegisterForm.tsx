import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { registerCandidateRequest, registerHrRequest } from './api'
import type { Role } from './types'

type AccountType = Extract<Role, 'CANDIDATE' | 'HR'>

const registerSchema = z
  .object({
    fullName: z.string().min(1, 'Vui lòng nhập họ tên'),
    email: z.string().email('Email không hợp lệ'),
    phone: z.string().optional(),
    password: z.string().min(8, 'Mật khẩu tối thiểu 8 ký tự'),
    confirmPassword: z.string().min(1, 'Vui lòng xác nhận mật khẩu'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Mật khẩu xác nhận không khớp',
    path: ['confirmPassword'],
  })

type RegisterFormValues = z.infer<typeof registerSchema>

const TABS: { value: AccountType; label: string }[] = [
  { value: 'CANDIDATE', label: 'Ứng viên' },
  { value: 'HR', label: 'Nhà tuyển dụng' },
]

export function RegisterForm() {
  const navigate = useNavigate()
  const [accountType, setAccountType] = useState<AccountType>('CANDIDATE')
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) })

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null)
    const payload = {
      email: values.email,
      password: values.password,
      fullName: values.fullName,
      phone: values.phone || undefined,
    }
    try {
      if (accountType === 'CANDIDATE') {
        await registerCandidateRequest(payload)
      } else {
        await registerHrRequest(payload)
      }
      navigate('/login', { state: { justRegistered: true } })
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 409) {
        setServerError('Email này đã được đăng ký')
      } else {
        setServerError('Đăng ký thất bại, vui lòng thử lại')
      }
    }
  })

  function handleTabChange(next: AccountType) {
    setAccountType(next)
    setServerError(null)
    reset()
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex rounded-md border border-line p-1" role="tablist">
        {TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            role="tab"
            aria-selected={accountType === tab.value}
            onClick={() => handleTabChange(tab.value)}
            className={`h-9 flex-1 rounded-[--radius-badge] text-sm font-medium transition-colors ${
              accountType === tab.value ? 'bg-brand text-white' : 'text-ink-muted'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="register-fullName" className="text-sm font-medium text-ink">
            Họ và tên
          </label>
          <input
            id="register-fullName"
            autoComplete="name"
            className="h-10 rounded-md border border-line px-3 text-sm text-ink"
            {...register('fullName')}
          />
          {errors.fullName && <p className="text-sm text-danger">{errors.fullName.message}</p>}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="register-email" className="text-sm font-medium text-ink">
            Email
          </label>
          <input
            id="register-email"
            type="email"
            autoComplete="email"
            className="h-10 rounded-md border border-line px-3 text-sm text-ink"
            {...register('email')}
          />
          {errors.email && <p className="text-sm text-danger">{errors.email.message}</p>}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="register-phone" className="text-sm font-medium text-ink">
            Số điện thoại (tuỳ chọn)
          </label>
          <input
            id="register-phone"
            type="tel"
            autoComplete="tel"
            className="h-10 rounded-md border border-line px-3 text-sm text-ink"
            {...register('phone')}
          />
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="register-password" className="text-sm font-medium text-ink">
            Mật khẩu
          </label>
          <input
            id="register-password"
            type="password"
            autoComplete="new-password"
            className="h-10 rounded-md border border-line px-3 text-sm text-ink"
            {...register('password')}
          />
          {errors.password && <p className="text-sm text-danger">{errors.password.message}</p>}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="register-confirmPassword" className="text-sm font-medium text-ink">
            Xác nhận mật khẩu
          </label>
          <input
            id="register-confirmPassword"
            type="password"
            autoComplete="new-password"
            className="h-10 rounded-md border border-line px-3 text-sm text-ink"
            {...register('confirmPassword')}
          />
          {errors.confirmPassword && <p className="text-sm text-danger">{errors.confirmPassword.message}</p>}
        </div>

        {serverError && <p className="text-sm text-danger">{serverError}</p>}

        <button
          type="submit"
          disabled={isSubmitting}
          className="h-10 rounded-md bg-brand px-5 text-sm font-medium text-white disabled:opacity-60"
        >
          {isSubmitting ? 'Đang đăng ký...' : 'Đăng ký'}
        </button>
      </form>
    </div>
  )
}

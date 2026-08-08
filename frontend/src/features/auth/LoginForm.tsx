import { zodResolver } from '@hookform/resolvers/zod'
import { isAxiosError } from 'axios'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from './useAuth'

const loginSchema = z.object({
  email: z.string().email('Email không hợp lệ'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
})

type LoginFormValues = z.infer<typeof loginSchema>

export function LoginForm() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) })

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null)
    try {
      const user = await login(values)
      navigate(user.role === 'HR' ? '/hr' : '/candidate', { replace: true })
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 401) {
        setServerError('Email hoặc mật khẩu không đúng')
      } else {
        setServerError('Đăng nhập thất bại, vui lòng thử lại')
      }
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <label htmlFor="login-email" className="text-sm font-medium text-ink">
          Email
        </label>
        <input
          id="login-email"
          type="email"
          autoComplete="email"
          className="h-10 rounded-md border border-line px-3 text-sm text-ink"
          {...register('email')}
        />
        {errors.email && <p className="text-sm text-danger">{errors.email.message}</p>}
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="login-password" className="text-sm font-medium text-ink">
          Mật khẩu
        </label>
        <input
          id="login-password"
          type="password"
          autoComplete="current-password"
          className="h-10 rounded-md border border-line px-3 text-sm text-ink"
          {...register('password')}
        />
        {errors.password && <p className="text-sm text-danger">{errors.password.message}</p>}
      </div>

      {serverError && <p className="text-sm text-danger">{serverError}</p>}

      <button
        type="submit"
        disabled={isSubmitting}
        className="h-10 rounded-md bg-brand px-5 text-sm font-medium text-white disabled:opacity-60"
      >
        {isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
      </button>
    </form>
  )
}

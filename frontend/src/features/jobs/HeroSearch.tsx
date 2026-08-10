import { Search } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'

export function HeroSearch() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '')
  const [location, setLocation] = useState(searchParams.get('location') ?? '')
  const [category, setCategory] = useState(searchParams.get('category') ?? '')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next = new URLSearchParams()
    if (keyword.trim()) next.set('keyword', keyword.trim())
    if (location.trim()) next.set('location', location.trim())
    if (category.trim()) next.set('category', category.trim())
    setSearchParams(next)
  }

  return (
    <section className="bg-brand py-12">
      <div className="mx-auto max-w-[1200px] px-4 md:px-6">
        <h1 className="text-center text-2xl font-semibold text-white">Tìm việc làm phù hợp với bạn</h1>
        <form
          onSubmit={handleSubmit}
          className="mx-auto mt-6 flex max-w-3xl flex-col gap-3 rounded-(--radius-card) bg-surface p-3 sm:flex-row"
        >
          <input
            type="text"
            placeholder="Từ khoá (vị trí, kỹ năng...)"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            className="h-10 flex-1 rounded-md border border-line px-3 text-sm text-ink"
          />
          <input
            type="text"
            placeholder="Địa điểm"
            value={location}
            onChange={(event) => setLocation(event.target.value)}
            className="h-10 flex-1 rounded-md border border-line px-3 text-sm text-ink"
          />
          <input
            type="text"
            placeholder="Ngành nghề"
            value={category}
            onChange={(event) => setCategory(event.target.value)}
            className="h-10 flex-1 rounded-md border border-line px-3 text-sm text-ink"
          />
          <button
            type="submit"
            className="flex h-10 items-center justify-center gap-2 rounded-md bg-brand px-5 text-sm font-medium text-white"
          >
            <Search size={16} />
            Tìm kiếm
          </button>
        </form>
      </div>
    </section>
  )
}

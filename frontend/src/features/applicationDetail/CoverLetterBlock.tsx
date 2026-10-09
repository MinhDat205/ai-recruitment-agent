import { useEffect, useId, useRef, useState } from 'react'

// FR-H09 R-L, UI.md muc 5c - thu gioi thieu cua UNG VIEN (khong phai noi dung AI): van ban thuan,
// giu xuong dong, tu ngat tu dai. KHONG render HTML/Markdown, KHONG tu nhan dang lien ket, KHONG
// trim/cat noi dung (R-D8) - React tu escape chuoi. Khong dat trong khoi "Do AI tao", khong kieu
// evidence (R-L3).
//
// Thu gon mac dinh o 6 dong; nut "Xem thêm" chi hien khi noi dung THAT SU bi cat - do bang
// scrollHeight > clientHeight trong callback cua ResizeObserver (chay ngay khi observe va moi lan doi
// kich thuoc), nen khong setState truc tiep trong than effect.
export function CoverLetterBlock({ text }: { text: string }) {
  const textId = useId()
  const textRef = useRef<HTMLParagraphElement>(null)
  const [expanded, setExpanded] = useState(false)
  const [overflowing, setOverflowing] = useState(false)

  useEffect(() => {
    const element = textRef.current
    if (!element || expanded) {
      // Dang mo rong thi khong do lai: line-clamp da bo nen scrollHeight == clientHeight, giu nguyen
      // ket qua do luc thu gon de nut "Thu gọn" van hien.
      return
    }
    const observer = new ResizeObserver(() => {
      setOverflowing(element.scrollHeight > element.clientHeight + 1)
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [expanded, text])

  return (
    <div className="flex flex-col gap-1">
      <h3 className="text-sm font-medium text-m3-on-surface">Thư giới thiệu</h3>
      <p
        id={textId}
        ref={textRef}
        className={`text-m3-body-md whitespace-pre-wrap break-words text-m3-on-surface ${expanded ? '' : 'line-clamp-6'}`}
      >
        {text}
      </p>
      {overflowing && (
        <button
          type="button"
          className="self-start text-sm font-medium text-m3-primary hover:underline"
          aria-expanded={expanded}
          aria-controls={textId}
          onClick={() => setExpanded((value) => !value)}
        >
          {expanded ? 'Thu gọn' : 'Xem thêm'}
        </button>
      )}
    </div>
  )
}

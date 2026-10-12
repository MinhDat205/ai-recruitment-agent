import { SCREENING_QUESTION_TYPE_LABELS, SCREENING_TEXT } from './screeningLabels'
import type { ApplicationScreeningItem } from './types'

// Chu khac nhau giua hai phia (HR: "Ung vien khong tra loi...", ung vien: "Ban khong tra loi...") truyen vao qua props,
// component KHONG re nhanh theo vai tro.
export interface ScreeningAnswerTexts {
  yes: string
  no: string
  noAnswer: string
}

// FR-U09 R-C5 - danh sach ban chup cau hoi + cau tra loi dung chung cho tab "Sang loc" phia HR (FR-H10 UI.md 4d) va khoi
// "Cau tra loi sang loc" phia ung vien (FR-U09 UI.md 4e). Markup chuyen NGUYEN tu ScreeningTab.tsx: <ol>, moi cau mot <li>,
// "Co", "Khong", chu phuong an va cau tra loi ngan cung kieu chu, cung mau (R-N4). Khong goi API, khong import gi cua
// phia HR hay phia ung vien. Moi chuoi la van ban thuong, khong render HTML.
export function ScreeningAnswerList({
  items,
  texts,
}: {
  items: readonly ApplicationScreeningItem[]
  texts: ScreeningAnswerTexts
}) {
  return (
    <ol className="flex flex-col">
      {[...items]
        .sort((a, b) => a.displayOrder - b.displayOrder)
        .map((item, index) => (
          <ScreeningAnswerItem key={item.displayOrder} item={item} position={index + 1} texts={texts} />
        ))}
    </ol>
  )
}

// SINGLE_CHOICE: answerText la chu cua phuong an da chon trong ban chup - chi hien chu do, khong liet ke cac phuong an
// con lai, khong danh dau (UI.md 4d). "Co", "Khong", chu phuong an va cau tra loi ngan cung kieu chu, cung mau (R-N4).
function answerOf(item: ApplicationScreeningItem, texts: ScreeningAnswerTexts): string | null {
  if (item.questionType === 'YES_NO') {
    return item.answerYesNo === null ? null : item.answerYesNo ? texts.yes : texts.no
  }
  return item.answerText
}

function ScreeningAnswerItem({
  item,
  position,
  texts,
}: {
  item: ApplicationScreeningItem
  position: number
  texts: ScreeningAnswerTexts
}) {
  const answer = answerOf(item, texts)
  const meta = `${SCREENING_QUESTION_TYPE_LABELS[item.questionType]} · ${
    item.required ? SCREENING_TEXT.required : SCREENING_TEXT.notRequired
  }`
  return (
    <li className="flex min-w-0 gap-1 border-t border-m3-outline-variant py-4 first:border-t-0 first:pt-0">
      <span className="shrink-0 text-m3-title-md text-m3-on-surface">{position}.</span>
      <div className="flex min-w-0 flex-col gap-1">
        <p className="text-m3-title-md break-words text-m3-on-surface">{item.questionText}</p>
        <p className="text-m3-body-sm text-m3-on-surface-variant">{meta}</p>
        {answer === null ? (
          <p className="text-m3-body-md text-m3-on-surface-variant">{texts.noAnswer}</p>
        ) : (
          <p className="text-m3-body-md break-words whitespace-pre-wrap text-m3-on-surface">{answer}</p>
        )}
      </div>
    </li>
  )
}

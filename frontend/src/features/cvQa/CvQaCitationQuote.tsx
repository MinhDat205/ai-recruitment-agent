// FR-C08 - mot trich dan nguyen van tu CV. DUNG class evidence H06 (features/scoring/CriterionScoreBreakdown.tsx:75-79,
// UI.md muc 5d) - khong mau khac, khong to sang/gach chan. Van ban thuan, khong HTML.
export function CvQaCitationQuote({ quote }: { quote: string }) {
  return (
    <blockquote className="border-l-2 border-m3-primary bg-m3-primary-container px-3 py-2 text-sm font-normal text-m3-on-surface">
      <p className="whitespace-pre-wrap break-words">{quote}</p>
    </blockquote>
  )
}

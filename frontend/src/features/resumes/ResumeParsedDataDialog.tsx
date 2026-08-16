import type { ReactNode } from 'react'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { useResumeParsedDataQuery } from './queries'
import type {
  ResumeParsedCertification,
  ResumeParsedContact,
  ResumeParsedEducation,
  ResumeParsedExperience,
  ResumeParsedProject,
} from './types'

function EmptyState({ message }: { message: string }) {
  return <p className="text-sm text-ink-muted">{message}</p>
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2 border-b border-line pb-4 last:border-b-0 last:pb-0">
      <h3 className="text-sm font-medium text-ink">{title}</h3>
      {children}
    </div>
  )
}

// Ghep "start - end" nhung khong tu bia phan con thieu (vd khong tu dien "Hien tai" khi endDate
// vang mat - CV that co the don gian khong ghi ngay ket thuc, khac voi "dang lam viec").
function formatDateRange(startDate: string | null, endDate: string | null): string | null {
  if (startDate && endDate) {
    return `${startDate} - ${endDate}`
  }
  return startDate ?? endDate ?? null
}

function ContactSection({ contact }: { contact: ResumeParsedContact | null }) {
  if (!contact) {
    return <EmptyState message="Không trích xuất được thông tin liên hệ" />
  }
  const fields: Array<[string, string | null]> = [
    ['Họ tên', contact.fullName],
    ['Email', contact.email],
    ['Điện thoại', contact.phone],
    ['Địa chỉ', contact.address],
    ['LinkedIn', contact.linkedin],
  ]
  const filled = fields.filter(([, value]) => value)
  if (filled.length === 0) {
    return <EmptyState message="Không trích xuất được thông tin liên hệ" />
  }
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-1 sm:grid-cols-2">
      {filled.map(([label, value]) => (
        <div key={label} className="flex gap-2 text-sm">
          <dt className="shrink-0 text-ink-muted">{label}:</dt>
          <dd className="text-ink">{value}</dd>
        </div>
      ))}
    </dl>
  )
}

function EducationSection({ education }: { education: ResumeParsedEducation[] }) {
  if (education.length === 0) {
    return <EmptyState message="CV không có mục học vấn" />
  }
  return (
    <ul className="flex flex-col gap-3">
      {education.map((item, index) => {
        const meta = [item.major, item.gpa != null ? `GPA ${item.gpa}` : null, formatDateRange(item.startDate, item.endDate)]
          .filter((value): value is string => Boolean(value))
          .join(' · ')
        return (
          <li key={index} className="text-sm">
            <p className="font-medium text-ink">
              {item.school ?? 'Chưa rõ trường'}
              {item.degree ? ` — ${item.degree}` : ''}
            </p>
            {meta && <p className="text-ink-muted">{meta}</p>}
          </li>
        )
      })}
    </ul>
  )
}

function ExperienceSection({ experience }: { experience: ResumeParsedExperience[] }) {
  if (experience.length === 0) {
    return <EmptyState message="CV không có mục kinh nghiệm làm việc" />
  }
  return (
    <ul className="flex flex-col gap-3">
      {experience.map((item, index) => {
        const range = formatDateRange(item.startDate, item.endDate)
        return (
          <li key={index} className="text-sm">
            <p className="font-medium text-ink">
              {item.title ?? 'Chưa rõ vị trí'}
              {item.company ? ` — ${item.company}` : ''}
            </p>
            {range && <p className="text-ink-muted">{range}</p>}
            {item.description && <p className="mt-1 whitespace-pre-line text-ink">{item.description}</p>}
          </li>
        )
      })}
    </ul>
  )
}

function SkillsSection({ skills }: { skills: string[] }) {
  if (skills.length === 0) {
    return <EmptyState message="CV không có mục kỹ năng" />
  }
  return (
    <div className="flex flex-wrap gap-2">
      {skills.map((skill) => (
        <span key={skill} className="rounded-(--radius-badge) bg-brand-light px-2 py-1 text-xs text-brand">
          {skill}
        </span>
      ))}
    </div>
  )
}

function CertificationsSection({ certifications }: { certifications: ResumeParsedCertification[] }) {
  if (certifications.length === 0) {
    return <EmptyState message="CV không có mục chứng chỉ" />
  }
  return (
    <ul className="flex flex-col gap-2">
      {certifications.map((item, index) => (
        <li key={index} className="text-sm">
          <span className="font-medium text-ink">{item.name ?? 'Chưa rõ tên chứng chỉ'}</span>
          {[item.issuer, item.issueDate].filter(Boolean).length > 0 && (
            <span className="text-ink-muted"> — {[item.issuer, item.issueDate].filter(Boolean).join(' · ')}</span>
          )}
        </li>
      ))}
    </ul>
  )
}

function ProjectsSection({ projects }: { projects: ResumeParsedProject[] }) {
  if (projects.length === 0) {
    return <EmptyState message="CV không có mục dự án" />
  }
  return (
    <ul className="flex flex-col gap-3">
      {projects.map((item, index) => (
        <li key={index} className="text-sm">
          <p className="font-medium text-ink">{item.name ?? 'Chưa rõ tên dự án'}</p>
          {item.description && <p className="mt-1 whitespace-pre-line text-ink">{item.description}</p>}
          {item.technologies.length > 0 && (
            <div className="mt-1 flex flex-wrap gap-2">
              {item.technologies.map((tech) => (
                <span key={tech} className="rounded-(--radius-badge) bg-brand-light px-2 py-1 text-xs text-brand">
                  {tech}
                </span>
              ))}
            </div>
          )}
        </li>
      ))}
    </ul>
  )
}

function ParsedDataSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-hidden="true">
      <div className="h-4 w-1/3 animate-pulse rounded bg-canvas" />
      <div className="h-16 animate-pulse rounded bg-canvas" />
      <div className="h-4 w-1/4 animate-pulse rounded bg-canvas" />
      <div className="h-16 animate-pulse rounded bg-canvas" />
    </div>
  )
}

interface ResumeParsedDataDialogProps {
  resumeId: string
  fileName: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ResumeParsedDataDialog({ resumeId, fileName, open, onOpenChange }: ResumeParsedDataDialogProps) {
  const { data, isLoading } = useResumeParsedDataQuery(resumeId, open)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Dữ liệu đã trích xuất — {fileName}</DialogTitle>
        </DialogHeader>

        {isLoading && <ParsedDataSkeleton />}

        {!isLoading && !data && <EmptyState message="Chưa có dữ liệu trích xuất cho CV này." />}

        {!isLoading && data && (
          <div className="flex flex-col gap-4">
            <Section title="Thông tin liên hệ">
              <ContactSection contact={data.data.contact} />
            </Section>
            <Section title="Học vấn">
              <EducationSection education={data.data.education} />
            </Section>
            <Section title="Kinh nghiệm">
              <ExperienceSection experience={data.data.experience} />
            </Section>
            <Section title="Kỹ năng">
              <SkillsSection skills={data.data.skills} />
            </Section>
            <Section title="Chứng chỉ">
              <CertificationsSection certifications={data.data.certifications} />
            </Section>
            <Section title="Dự án">
              <ProjectsSection projects={data.data.projects} />
            </Section>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

import { Mail, MapPin, Phone, Users, Globe } from 'lucide-react'
import type { CompanyPublic } from './types'

export function CompanyProfileCard({ company }: { company: CompanyPublic }) {
  return (
    <div className="rounded-(--radius-card) border border-line bg-surface p-6">
      <div className="flex items-center gap-4">
        <div className="h-20 w-20 shrink-0 overflow-hidden rounded-(--radius-badge) border border-line bg-canvas">
          {company.logoUrl && (
            <img src={company.logoUrl} alt={company.name} className="h-full w-full object-cover" />
          )}
        </div>
        <div>
          <h1 className="text-xl font-semibold text-ink">{company.name}</h1>
          {company.industry && <p className="text-sm text-ink-muted">{company.industry}</p>}
        </div>
      </div>

      {company.description && <p className="mt-4 whitespace-pre-wrap text-sm text-ink">{company.description}</p>}

      <div className="mt-4 flex flex-col gap-2 text-sm text-ink-muted">
        {company.companySize && (
          <div className="flex items-center gap-2">
            <Users size={16} />
            <span>{company.companySize}</span>
          </div>
        )}
        {company.address && (
          <div className="flex items-center gap-2">
            <MapPin size={16} />
            <span>{company.address}</span>
          </div>
        )}
        {company.website && (
          <div className="flex items-center gap-2">
            <Globe size={16} />
            <a href={company.website} target="_blank" rel="noreferrer" className="text-brand hover:underline">
              {company.website}
            </a>
          </div>
        )}
        {company.contactEmail && (
          <div className="flex items-center gap-2">
            <Mail size={16} />
            <span>{company.contactEmail}</span>
          </div>
        )}
        {company.contactPhone && (
          <div className="flex items-center gap-2">
            <Phone size={16} />
            <span>{company.contactPhone}</span>
          </div>
        )}
      </div>
    </div>
  )
}

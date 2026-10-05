import { Mail, MapPin, Phone, Users, Globe } from 'lucide-react'
import type { CompanyPublic } from './types'

export function CompanyProfileCard({ company }: { company: CompanyPublic }) {
  return (
    <div className="rounded-(--radius-card) border border-m3-outline-variant bg-m3-surface p-6">
      <div className="flex items-center gap-4">
        <div className="h-20 w-20 shrink-0 overflow-hidden rounded-(--radius-badge) border border-m3-outline-variant bg-m3-surface-container">
          {company.logoUrl && (
            <img src={company.logoUrl} alt={company.name} className="h-full w-full object-cover" />
          )}
        </div>
        <div>
          <h1 className="text-xl font-semibold text-m3-on-surface">{company.name}</h1>
          {company.industry && <p className="text-sm text-m3-on-surface-variant">{company.industry}</p>}
        </div>
      </div>

      {company.description && <p className="mt-4 whitespace-pre-wrap text-sm text-m3-on-surface">{company.description}</p>}

      <div className="mt-4 flex flex-col gap-2 text-sm text-m3-on-surface-variant">
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
            <a href={company.website} target="_blank" rel="noreferrer" className="text-m3-primary hover:underline">
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

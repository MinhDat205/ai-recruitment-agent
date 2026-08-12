package com.recruitment.interviewtemplate;

import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.InterviewTemplateNotFoundException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.interviewtemplate.dto.InterviewTemplateRequest;
import com.recruitment.interviewtemplate.dto.InterviewTemplateResponse;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Template gan 1-1 voi Job, nen quyen so huu di qua Job -> Company cua HR dang dang nhap,
// giong het pattern cua JobOwnerService.loadOwned() - khong chi dua vao hasRole("HR").
@Service
public class InterviewTemplateOwnerService {

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final InterviewTemplateRepository interviewTemplateRepository;

    public InterviewTemplateOwnerService(
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            InterviewTemplateRepository interviewTemplateRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.interviewTemplateRepository = interviewTemplateRepository;
    }

    public InterviewTemplateResponse getMine(UUID ownerId, UUID jobId) {
        loadOwnedJob(jobId, ownerId);
        return toResponse(loadTemplate(jobId));
    }

    @Transactional
    public InterviewTemplateResponse update(UUID ownerId, UUID jobId, InterviewTemplateRequest request) {
        loadOwnedJob(jobId, ownerId);
        InterviewTemplate template = loadTemplate(jobId);
        template.setSubject(request.subject());
        template.setBody(request.body());
        template.setSenderName(request.senderName());
        template.setSenderTitle(request.senderTitle());
        template.setAddress(request.address());
        return toResponse(interviewTemplateRepository.save(template));
    }

    private Job loadOwnedJob(UUID jobId, UUID ownerId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        Company company = companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
        if (!job.getCompanyId().equals(company.getId())) {
            // AccessDeniedException chuan cua Spring Security -> JsonAccessDeniedHandler tra 403 JSON.
            throw new AccessDeniedException("Khong co quyen truy cap mau giay moi phong van nay");
        }
        return job;
    }

    private InterviewTemplate loadTemplate(UUID jobId) {
        return interviewTemplateRepository
                .findByJobId(jobId)
                .orElseThrow(() -> new InterviewTemplateNotFoundException(jobId));
    }

    private InterviewTemplateResponse toResponse(InterviewTemplate t) {
        return new InterviewTemplateResponse(
                t.getId(),
                t.getJobId(),
                t.getCompanyName(),
                t.getSubject(),
                t.getBody(),
                t.getSenderName(),
                t.getSenderTitle(),
                t.getAddress(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}

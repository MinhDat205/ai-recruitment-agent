package com.recruitment.aicontext;

import com.recruitment.aicontext.ConversationContext.InterviewSchedule;
import com.recruitment.aicontext.ConversationContext.RecentMessage;
import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.interviewinvitation.InterviewInvitationRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.messaging.ApplicationMessage;
import com.recruitment.messaging.ApplicationMessageRepository;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// FR-C07 K1 (R-K1-3, R-K1-4) - gom ngu canh cuoc trao doi cua MOT don. Goi SAU khi noi goi da kiem quyen tren don
// (R-Q1) - o day khong kiem quyen. Transaction CHI DOC, NGAN: chi nap du lieu, dong truoc khi noi goi goi AI (CLAUDE.md
// muc 3c) - bean rieng, duoc inject vao noi goi, khong tu goi trong cung bean (self-invocation pha @Transactional).
//
// Phu thuoc DUNG danh sach R-K1-4: jobapplication/, job/, company/, user/, interviewinvitation/, messaging/, common/.
// CAM scoring/, rubric/, resume/, ai/, jobrecommendation/ (T10 kiem bang reflection). Khong them method repository moi.
@Component
public class ConversationContextAssembler {

    static final int RECENT_MESSAGE_LIMIT = 10;

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final InterviewInvitationRepository interviewInvitationRepository;
    private final ApplicationMessageRepository messageRepository;

    public ConversationContextAssembler(
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            UserRepository userRepository,
            InterviewInvitationRepository interviewInvitationRepository,
            ApplicationMessageRepository messageRepository) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.interviewInvitationRepository = interviewInvitationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public ConversationContext forConversation(ContextViewer viewer, UUID applicationId) {
        JobApplication application = jobApplicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        Job job = jobRepository
                .findById(application.getJobId())
                .orElseThrow(() -> new JobNotFoundException(application.getJobId()));
        Company company = companyRepository
                .findById(job.getCompanyId())
                .orElseThrow(() -> new CompanyNotFoundException(job.getCompanyId()));
        // candidate_id la FK NOT NULL toi users - thieu la loi du lieu, khong phai loi nguoi dung.
        User candidate = userRepository
                .findById(application.getCandidateId())
                .orElseThrow(() -> new IllegalStateException(
                        "Khong tim thay ung vien cua don " + applicationId));

        // Giay moi moi nhat - cung cach phia ung vien doc (InterviewInvitationService.getLatestInvitationForCandidate).
        InterviewSchedule interview = interviewInvitationRepository
                .findByApplicationIdOrderByCreatedAtDesc(applicationId)
                .stream()
                .findFirst()
                .map(invitation -> new InterviewSchedule(invitation.getScheduledAt(), invitation.getLocation()))
                .orElse(null);

        return new ConversationContext(
                viewer,
                candidate.getFullName(),
                job.getTitle(),
                company.getName(),
                application.getStatus(),
                interview,
                recentMessages(applicationId));
    }

    // findLatest tra MOI NHAT truoc (C06 R-M7) - dao lai thanh cu truoc moi sau. Noi dung giu NGUYEN VAN (L7).
    private List<RecentMessage> recentMessages(UUID applicationId) {
        List<ApplicationMessage> latest =
                messageRepository.findLatest(applicationId, PageRequest.of(0, RECENT_MESSAGE_LIMIT));
        List<RecentMessage> messages = new ArrayList<>(latest.size());
        for (ApplicationMessage message : latest) {
            messages.add(new RecentMessage(
                    message.getSenderRole(), message.getBody(), message.getAttachmentKey() != null));
        }
        Collections.reverse(messages);
        return messages;
    }
}

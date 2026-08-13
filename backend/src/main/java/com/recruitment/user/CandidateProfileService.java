package com.recruitment.user;

import com.recruitment.user.dto.CandidateProfileRequest;
import com.recruitment.user.dto.CandidateProfileResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateProfileService {

    private final CandidateProfileRepository candidateProfileRepository;

    public CandidateProfileService(CandidateProfileRepository candidateProfileRepository) {
        this.candidateProfileRepository = candidateProfileRepository;
    }

    @Transactional
    public CandidateProfileResponse getMine(UUID userId) {
        return toResponse(loadOrCreate(userId));
    }

    @Transactional
    public CandidateProfileResponse update(UUID userId, CandidateProfileRequest request) {
        CandidateProfile profile = loadOrCreate(userId);
        profile.setHeadline(request.headline());
        profile.setLocation(request.location());
        profile.setCurrentTitle(request.currentTitle());
        profile.setYearsExperience(request.yearsExperience());
        profile.setDateOfBirth(request.dateOfBirth());
        return toResponse(candidateProfileRepository.save(profile));
    }

    // Ho so duoc tao san luc dang ky (xem AuthService.registerCandidate) nen ly thuyet luon co san
    // o day. Van tu tao neu thieu de GET/PUT khong bao gio tra 500 vi mot ban ghi bi thieu du lieu.
    private CandidateProfile loadOrCreate(UUID userId) {
        return candidateProfileRepository
                .findByUserId(userId)
                .orElseGet(() -> candidateProfileRepository.save(new CandidateProfile(userId)));
    }

    private static CandidateProfileResponse toResponse(CandidateProfile p) {
        return new CandidateProfileResponse(
                p.getId(),
                p.getHeadline(),
                p.getLocation(),
                p.getCurrentTitle(),
                p.getYearsExperience(),
                p.getDateOfBirth(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}

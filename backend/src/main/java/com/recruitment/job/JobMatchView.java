package com.recruitment.job;

import java.math.BigDecimal;
import java.util.UUID;

public interface JobMatchView {
    UUID getJobId();

    BigDecimal getSimilarityScore();
}

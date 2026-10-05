package com.recruitment.job;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// FR-U07 R-T2 (muc 7.7) - nguong postedWithin tinh boi Clock inject duoc (ClockConfig.java), KHONG
// phu thuoc dong ho that cua may chay test. @Primary de ghi de bean Clock.systemUTC() mac dinh CHI
// trong context test nao import class nay (JobPublicIntegrationTest) - cung mau voi LlmTestConfiguration
// (stubChatModel/@Primary) da dung san trong du an.
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockTestConfiguration {

    public static final Instant FIXED_NOW = Instant.parse("2026-06-15T12:00:00Z");

    // Ten method KHONG duoc trung "clock" - Spring dat ten bean theo ten method; trung ten voi bean
    // "clock" cua ClockConfig se nem BeanDefinitionOverrideException khi khoi dong (spring.main.
    // allow-bean-definition-overriding mac dinh false, @Primary KHONG giup duoc truong hop trung
    // TEN, chi giup khi hai bean CUNG KIEU nhung KHAC ten). Phat hien thuc nghiem o dot 2 (chay
    // JobPublic* that), khong phai suy doan.
    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }
}

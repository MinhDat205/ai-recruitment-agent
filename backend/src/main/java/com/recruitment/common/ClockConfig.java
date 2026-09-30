package com.recruitment.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Dong ho inject duoc (FR-C05 R-E2): noi can "bay gio" cho mot gia tri nghiep vu duoc luu (moc tham
// chieu tinh kinh nghiem) lay tu bean nay, de test co dinh duoc thoi diem bang Clock.fixed. Mui gio
// nghiep vu (Asia/Ho_Chi_Minh) do noi dung tu ap dung, khong phu thuoc mui gio cua dong ho.
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

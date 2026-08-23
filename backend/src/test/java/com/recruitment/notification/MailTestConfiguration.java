package com.recruitment.notification;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

// Chan goi SMTP that - khuon LlmTestConfiguration (chan ChatModel) nhung KHONG dung default-answer
// throw: JavaMailSender.send(SimpleMailMessage) tra ve void, mock mac dinh cua Mockito la no-op
// (khong lam gi, khong nem, khong cham mang that) - du cho ca nhanh gui-thanh-cong (khong can stub
// gi them) lan nhanh gui-that-bai (tung test tu stub doThrow rieng). Khac ChatModel, khong co rui
// ro "am tham goi that" vi bean nay thay THE HOAN TOAN cho JavaMailSender that, khong boc qua tang
// trung gian nao co the vo tinh bo qua mock.
@TestConfiguration(proxyBeanMethods = false)
public class MailTestConfiguration {

    @Bean
    @Primary
    public JavaMailSender stubMailSender() {
        return Mockito.mock(JavaMailSender.class);
    }
}

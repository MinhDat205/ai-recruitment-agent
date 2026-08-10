package com.recruitment.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

// Khong cau hinh formLogin()/httpBasic() nen Spring Security khong co entry point mac dinh
// ro rang cho request chua xac thuc - phai khai bao rieng, neu khong se lan sang 403 sai.
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"UNAUTHENTICATED\",\"message\":\"Can dang nhap de truy cap tai nguyen nay\"}");
    }
}

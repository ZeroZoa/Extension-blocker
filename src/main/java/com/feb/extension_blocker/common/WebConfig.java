package com.feb.extension_blocker.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트엔드(로컬은 Vite 개발 서버, 배포 후에는 Vercel 도메인)에서 오는 요청을
 * 허용한다. 백엔드/프론트를 별도 저장소·별도 플랫폼(Render/Vercel)으로 배포하기로
 * 한 결정에 따라 브라우저의 동일 출처 정책(CORS)을 명시적으로 풀어줘야 한다.
 *
 * <p>전체 허용({@code *}) 대신 허용 오리진을 환경변수로 명시 지정한다 — 이 API는
 * 파일 업로드를 다루므로, 임의의 사이트가 이용자 브라우저를 거쳐 요청을 보낼 수
 * 있게 열어두는 건 불필요한 공격 표면이다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}

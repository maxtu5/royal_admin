package com.tuiken.royaladmin.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        ClientHttpRequestInterceptor userAgentInterceptor = (request, body, execution) -> {
            request.getHeaders().add("User-Agent", "LinkResolverBot/1.0 (yourname@example.com)");
            return execution.execute(request, body);
        };

        restTemplate.setInterceptors(List.of(userAgentInterceptor));
        return restTemplate;
    }
}

package com.hoangluongtran0309.releaseflow.configuration;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.ErrorResponse;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.util.List;

@Configuration(proxyBeanMethods = false)
class ProblemDetailConfiguration implements WebMvcConfigurer {

    private final HttpServletRequest request;

    ProblemDetailConfiguration(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public void addErrorResponseInterceptors(List<ErrorResponse.Interceptor> interceptors) {
        // MVC used the current dispatch path through Framework 7.0, including /error
        // on an error dispatch. Security writes its responses before MVC.
        interceptors.add((problem, errorResponse) -> {
            if (problem.getInstance() == null) {
                problem.setInstance(URI.create(request.getRequestURI()));
            }
        });
    }
}

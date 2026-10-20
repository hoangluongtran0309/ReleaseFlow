package com.hoangluongtran0309.releaseflow.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

/**
 * The refusals Spring MVC raises itself before a controller's own code runs: a method the
 * address does not take, a body type it does not read, a missing parameter, or one that
 * does not convert. Spring's own Problem Details for them carry message codes nobody
 * translated and no {@code code}; this answers an API caller with a stable code and
 * wording from the bundle, and a browser with the error page.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class FrameworkErrorAdvice {

    private final UiMessages messages;

    FrameworkErrorAdvice(UiMessages messages) {
        this.messages = messages;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> methodNotAllowed(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        return answer(request, response, HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> unsupportedMediaType(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        return answer(request, response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type");
    }

    @ExceptionHandler({TypeMismatchException.class, ServletRequestBindingException.class})
    ResponseEntity<ProblemDetail> invalidParameter(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        return answer(request, response, HttpStatus.BAD_REQUEST, "invalid_parameter");
    }

    private ResponseEntity<ProblemDetail> answer(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code
    ) throws IOException {
        if (UnknownAddressAdvice.wantsHtml(request)) {
            // The container's error dispatch renders the page, as for any other status.
            response.sendError(status.value());
            return null;
        }
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(messages.get("error." + code + ".title"));
        problem.setDetail(messages.get("error." + code));
        problem.setProperty("code", code);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}

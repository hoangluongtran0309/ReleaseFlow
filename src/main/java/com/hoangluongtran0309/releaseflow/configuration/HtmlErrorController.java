package com.hoangluongtran0309.releaseflow.configuration;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * The page somebody lands on when a request fails outside a controller that has its own
 * answer: a wrong address, a page their role does not reach, or a fault. REST clients
 * get {@code application/problem+json} from {@link ApiExceptionHandler} and the security
 * chain, which both run first; whatever still fails under {@code /api} lands here too,
 * and is answered with Problem Details whose {@code code} is fixed and whose wording
 * never carries the exception behind it.
 *
 * <p>Only three statuses have wording of their own; anything else is shown as a fault,
 * because a page that guessed would be telling somebody something it does not know. A
 * refusal the security chain explained, such as a stale form, keeps its own wording.
 */
@Controller
class HtmlErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;
    private final UiMessages messages;

    HtmlErrorController(ErrorAttributes errorAttributes, UiMessages messages) {
        this.errorAttributes = errorAttributes;
        this.messages = messages;
    }

    @RequestMapping("/error")
    Object error(HttpServletRequest request, Model model) {
        HttpStatus status = status(request);
        if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String uri && uri.startsWith("/api/")) {
            return problem(status);
        }
        model.addAttribute("status", status.value());
        model.addAttribute("statusKey", switch (status) {
            case NOT_FOUND -> "notFound";
            case FORBIDDEN -> ApiAccessDeniedHandler.FORM_EXPIRED.equals(request.getAttribute(ApiAccessDeniedHandler.ERROR_REASON))
                    ? ApiAccessDeniedHandler.FORM_EXPIRED
                    : "forbidden";
            default -> "fault";
        });
        // The path is what the person typed or followed; never the exception behind it.
        model.addAttribute("path", errorAttributes
                .getErrorAttributes(new ServletWebRequest(request), ErrorAttributeOptions.defaults())
                .get("path"));
        return "error";
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status) {
        String code = switch (status) {
            case UNAUTHORIZED -> "authentication_required";
            case FORBIDDEN -> "access_denied";
            case NOT_FOUND -> "not_found";
            default -> status.is5xxServerError() ? "internal_error" : "request_failed";
        };
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(messages.get("error." + code + ".title"));
        problem.setDetail(messages.get("error." + code));
        problem.setProperty("code", code);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static HttpStatus status(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer value) {
            HttpStatus resolved = HttpStatus.resolve(value);
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

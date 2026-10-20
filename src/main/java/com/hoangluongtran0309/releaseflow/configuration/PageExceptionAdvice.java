package com.hoangluongtran0309.releaseflow.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * A page that names something this Organization does not have answers with the not-found
 * page rather than with itself and an alert: there is nothing on it to act on. The page
 * says which kind of thing was missing, in the failure's own words, and never whether it
 * exists somewhere else.
 *
 * <p>{@link ApiExceptionHandler} runs first and answers every API controller with its own
 * Problem Details code, so only pages reach this.
 */
@ControllerAdvice(annotations = Controller.class)
class PageExceptionAdvice {

    @ExceptionHandler(ResourceNotFoundException.class)
    String notFound(
            ResourceNotFoundException failure,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("status", HttpStatus.NOT_FOUND.value());
        model.addAttribute("statusKey", "notFound");
        model.addAttribute("detail", UiNotice.error(failure));
        model.addAttribute("path", request.getRequestURI());
        return "error";
    }
}

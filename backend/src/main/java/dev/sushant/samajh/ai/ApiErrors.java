package dev.sushant.samajh.ai;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Expected AI problems (no key, free-tier limit, slow model, no web search) are 503s with a readable message, not crashes. */
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(AiService.UserFacingException.class)
    ResponseEntity<Map<String, String>> userFacing(AiService.UserFacingException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
    }
}

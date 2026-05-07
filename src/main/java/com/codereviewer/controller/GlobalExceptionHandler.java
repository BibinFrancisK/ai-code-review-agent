package com.codereviewer.controller;

import com.codereviewer.util.Constants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAny(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        if (Constants.WEBHOOK_PATH.equals(req.getRequestURI())) {
            // GitHub retries delivery on non-2xx — always return 200 for webhooks
            return ResponseEntity.ok().build();
        }
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName();
        return ResponseEntity.internalServerError().body(Map.of("error", message));
    }
}

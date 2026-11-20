package com.capacitydesk.common.web;

import java.time.Instant;
import java.util.List;

/** Single error contract for every non-2xx response. */
public record ApiError(Instant timestamp, int status, String code, String message, String path, List<String> details) {

    public static ApiError of(int status, String code, String message, String path, List<String> details) {
        return new ApiError(Instant.now(), status, code, message, path, details);
    }
}

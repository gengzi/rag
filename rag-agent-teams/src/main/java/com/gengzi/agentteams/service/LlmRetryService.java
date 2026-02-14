package com.gengzi.agentteams.service;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;
import java.util.function.Supplier;

@Service
public class LlmRetryService {

    // 失败重试上限：总共最多调用 10 次
    private static final int MAX_ATTEMPTS = 10;

    public String executeWithRetry(Supplier<String> call) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException ex) {
                last = ex;
                if (!isRetryable(ex) || attempt >= MAX_ATTEMPTS) {
                    throw ex;
                }
                sleepBackoff(attempt);
            }
        }
        throw last == null ? new IllegalStateException("LLM call failed unexpectedly") : last;
    }

    private boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof RestClientResponseException responseException) {
                HttpStatusCode status = responseException.getStatusCode();
                int code = status.value();
                if (code == 429) {
                    return true;
                }
                // 某些网关会把额度耗尽映射为 403，这里按用户要求一并重试
                if (code == 403 && containsExhaustedHint(responseException.getResponseBodyAsString())) {
                    return true;
                }
            }
            if (containsExhaustedHint(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsExhaustedHint(String message) {
        if (message == null) {
            return false;
        }
        String m = message.toLowerCase(Locale.ROOT);
        return m.contains("http 429")
                || m.contains("all accounts exhausted")
                || m.contains("last error: http 403")
                || m.contains("status=429");
    }

    private void sleepBackoff(int attempt) {
        // 300ms, 600ms, 900ms ... 最多 3000ms
        long millis = Math.min(300L * attempt, 3000L);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM retry interrupted", e);
        }
    }
}

package com.civic_connect.backend.classifier;

import com.civic_connect.backend.classifier.dto.AiClassificationResult;
import com.civic_connect.backend.common.enums.IssueType;
import com.civic_connect.backend.common.enums.PriorityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiClassificationService {

    private static final Logger log = LoggerFactory.getLogger(AiClassificationService.class);

    private final WebClient webClient;
    private final String apiKey;
    private final String apiUrl;
    private final long timeoutMs;

    public AiClassificationService(
            WebClient.Builder webClientBuilder,
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent}") String apiUrl,
            @Value("${gemini.timeout-ms:8000}") long timeoutMs
    ) {
        this.webClient = webClientBuilder.build();
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.timeoutMs = timeoutMs;
    }

    public AiClassificationResult classify(String title, String description, String address, String area, String city) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Gemini API key not configured; skipping AI classification");
            return null;
        }
        try {
            String prompt = buildPrompt(title, description, address, area, city);
            Map<String, Object> body = buildRequestBody(prompt);

            String rawJson = webClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            return parseResult(rawJson);
        } catch (WebClientResponseException e) {
            log.warn("Gemini API request failed (status {}): {}", e.getStatusCode(), e.getStatusText());
            return null;
        } catch (Exception e) {
            log.warn("AI classification unavailable ({}); skipping: {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private String buildPrompt(String title, String description, String address, String area, String city) {
        return """
                You are a civic complaint classifier for an Indian municipal government.
                Analyse the complaint below and return ONLY a single JSON object with keys:
                category (one of: ROAD, GARBAGE, WATER, ELECTRICITY, DRAINAGE, SAFETY, OTHER),
                severity (one of: LOW, MEDIUM, HIGH, CRITICAL),
                suggestedDepartment (short string like "Public Works", "Water Board", "Electricity Dept", "Municipal Sanitation", "Fire/Safety", "General Admin"),
                confidence (number between 0.0 and 1.0).
                Do not wrap in markdown, do not add commentary, output only valid JSON.

                Complaint title: %s
                Complaint description: %s
                Location: %s, %s, %s
                """.formatted(
                safe(title), safe(description), safe(address), safe(area), safe(city)
        );
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        return Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 256
                )
        );
    }

    private AiClassificationResult parseResult(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return null;
        try {
            String text = extractNestedText(rawJson);
            if (text == null) return null;
            text = text.replaceAll("(?s)```json\\s*|```\\s*", "").trim();

            IssueType category = IssueType.valueOf(safeEnumValue(stringField(text, "category"), IssueType.OTHER));
            PriorityLevel severity = PriorityLevel.valueOf(safeEnumValue(stringField(text, "severity"), PriorityLevel.MEDIUM));
            String dept = stringField(text, "suggestedDepartment");
            Double confidence = numberField(text, "confidence");
            if (confidence == null) confidence = 0.0;
            if (confidence < 0.0) confidence = 0.0;
            if (confidence > 1.0) confidence = 1.0;

            return new AiClassificationResult(category, severity, dept, confidence, true, null);
        } catch (Exception e) {
            log.warn("Failed to parse AI classification output: {}", e.getMessage());
            return null;
        }
    }

    private static String extractNestedText(String raw) {
        try {
            int idx = raw.indexOf("\"text\"");
            if (idx < 0) return null;
            int colon = raw.indexOf(':', idx);
            if (colon < 0) return null;
            int quote = raw.indexOf('"', colon + 1);
            if (quote < 0) return null;
            StringBuilder sb = new StringBuilder();
            int i = quote + 1;
            while (i < raw.length()) {
                char ch = raw.charAt(i);
                if (ch == '\\' && i + 1 < raw.length()) {
                    char next = raw.charAt(i + 1);
                    switch (next) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        default: sb.append(next); break;
                    }
                    i += 2;
                } else if (ch == '"') {
                    return sb.toString();
                } else {
                    sb.append(ch);
                    i++;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static final Pattern STRING_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern NUM_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern BOOL_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(true|false)");

    private static String stringField(String json, String name) {
        Matcher m = STRING_FIELD.matcher(json);
        while (m.find()) {
            if (name.equals(m.group(1))) {
                return unescape(m.group(2));
            }
        }
        return null;
    }

    private static Double numberField(String json, String name) {
        Matcher m = NUM_FIELD.matcher(json);
        while (m.find()) {
            if (name.equals(m.group(1))) {
                try { return Double.parseDouble(m.group(2)); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static String unescape(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r").replace("\\/", "/");
    }

    private static <E extends Enum<E>> String safeEnumValue(String value, E fallback) {
        if (value == null || value.isBlank()) return fallback.name();
        String up = value.trim().toUpperCase();
        for (E c : fallback.getDeclaringClass().getEnumConstants()) {
            if (c.name().equals(up)) return up;
        }
        return fallback.name();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public static String toJson(AiClassificationResult r) {
        if (r == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"category\":").append(r.category() == null ? "null" : quote(r.category().name())).append(',');
        sb.append("\"severity\":").append(r.severity() == null ? "null" : quote(r.severity().name())).append(',');
        sb.append("\"suggestedDepartment\":").append(r.suggestedDepartment() == null ? "null" : quote(r.suggestedDepartment())).append(',');
        sb.append("\"confidence\":").append(r.confidence() == null ? "null" : String.valueOf(r.confidence())).append(',');
        sb.append("\"aiSuggested\":").append(r.aiSuggested()).append(',');
        sb.append("\"confirmedByAdminId\":").append(r.confirmedByAdminId() == null ? "null" : String.valueOf(r.confirmedByAdminId()));
        sb.append('}');
        return sb.toString();
    }

    public static AiClassificationResult fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            String catStr = stringField(json, "category");
            String sevStr = stringField(json, "severity");
            String dept = stringField(json, "suggestedDepartment");
            Double conf = numberField(json, "confidence");
            Boolean aiSug = null;
            Matcher bm = BOOL_FIELD.matcher(json);
            while (bm.find()) {
                if ("aiSuggested".equals(bm.group(1))) {
                    aiSug = Boolean.parseBoolean(bm.group(2));
                }
            }
            Long adminId = null;
            Matcher nm = NUM_FIELD.matcher(json);
            while (nm.find()) {
                if ("confirmedByAdminId".equals(nm.group(1))) {
                    try { adminId = (long) Double.parseDouble(nm.group(2)); } catch (Exception ignored) {}
                }
            }
            IssueType cat = catStr == null ? null : IssueType.valueOf(catStr);
            PriorityLevel sev = sevStr == null ? null : PriorityLevel.valueOf(sevStr);
            if (aiSug == null) aiSug = true;
            return new AiClassificationResult(cat, sev, dept, conf, aiSug, adminId);
        } catch (Exception e) {
            return null;
        }
    }

    private static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}

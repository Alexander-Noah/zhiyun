package org.example.backend.security;

import org.example.backend.entity.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_TYPE = "JWT";

    private final byte[] secret;
    private final long expirationSeconds;
    private final String issuer;

    public JwtService(
            @Value("${smart-lab.security.jwt.secret:smart-lab-dev-jwt-secret-change-in-production-2026}") String secret,
            @Value("${smart-lab.security.jwt.expiration-seconds:7200}") long expirationSeconds,
            @Value("${smart-lab.security.jwt.issuer:smart-lab}") String issuer
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = Math.max(expirationSeconds, 300);
        this.issuer = issuer == null ? "" : issuer.trim();
    }

    public String generateToken(UserEntity user) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + expirationSeconds;

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", TOKEN_TYPE);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getUsername());
        payload.put("uid", user.getId());
        payload.put("role", user.getRoleCode());
        payload.put("name", user.getRealName());
        payload.put("iss", issuer);
        payload.put("iat", issuedAt);
        payload.put("exp", expiresAt);

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signature = sign(encodedHeader + "." + encodedPayload);

        return encodedHeader + "." + encodedPayload + "." + signature;
    }

    public JwtClaims validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("missing token");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid token format");
        }

        String expectedSignature = sign(parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("invalid token signature");
        }

        String headerJson = decodeJson(parts[0]);
        if (!"HS256".equals(stringValue(jsonValue(headerJson, "alg"))) || !TOKEN_TYPE.equals(stringValue(jsonValue(headerJson, "typ")))) {
            throw new IllegalArgumentException("invalid token header");
        }

        String payloadJson = decodeJson(parts[1]);
        String tokenIssuer = stringValue(jsonValue(payloadJson, "iss"));
        if (!issuer.isBlank() && !issuer.equals(tokenIssuer)) {
            throw new IllegalArgumentException("invalid token issuer");
        }

        long expiresAt = numberValue(jsonValue(payloadJson, "exp"));
        if (expiresAt <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("token expired");
        }

        String username = stringValue(jsonValue(payloadJson, "sub"));
        if (username.isBlank()) {
            throw new IllegalArgumentException("token subject missing");
        }

        return new JwtClaims(
                username,
                stringValue(jsonValue(payloadJson, "role")),
                stringValue(jsonValue(payloadJson, "uid")),
                expiresAt
        );
    }

    private String encodeJson(Map<String, Object> value) {
        return base64Url(toJson(value).getBytes(StandardCharsets.UTF_8));
    }

    private String decodeJson(String value) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid token payload", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return base64Url(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("jwt sign failed", exception);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String toJson(Map<String, Object> value) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escapeJson(entry.getKey())).append("\":");
            appendJsonValue(json, entry.getValue());
        }
        return json.append('}').toString();
    }

    private void appendJsonValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
            return;
        }
        json.append('"').append(escapeJson(String.valueOf(value))).append('"');
    }

    private String jsonValue(String json, String key) {
        String keyToken = "\"" + escapeJson(key) + "\"";
        int keyIndex = json.indexOf(keyToken);
        if (keyIndex < 0) {
            return "";
        }

        int colonIndex = json.indexOf(':', keyIndex + keyToken.length());
        if (colonIndex < 0) {
            return "";
        }

        int valueIndex = colonIndex + 1;
        while (valueIndex < json.length() && Character.isWhitespace(json.charAt(valueIndex))) {
            valueIndex++;
        }

        if (valueIndex >= json.length()) {
            return "";
        }

        if (json.charAt(valueIndex) == '"') {
            return readJsonString(json, valueIndex + 1);
        }

        int endIndex = valueIndex;
        while (endIndex < json.length()) {
            char current = json.charAt(endIndex);
            if (current == ',' || current == '}') {
                break;
            }
            endIndex++;
        }
        String rawValue = json.substring(valueIndex, endIndex).trim();
        return "null".equals(rawValue) ? "" : rawValue;
    }

    private String readJsonString(String json, int startIndex) {
        StringBuilder value = new StringBuilder();
        for (int index = startIndex; index < json.length(); index++) {
            char current = json.charAt(index);
            if (current == '"') {
                return value.toString();
            }
            if (current != '\\' || index + 1 >= json.length()) {
                value.append(current);
                continue;
            }

            char escaped = json.charAt(++index);
            switch (escaped) {
                case '"' -> value.append('"');
                case '\\' -> value.append('\\');
                case '/' -> value.append('/');
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    if (index + 4 >= json.length()) {
                        throw new IllegalArgumentException("invalid json string escape");
                    }
                    String hex = json.substring(index + 1, index + 5);
                    value.append((char) Integer.parseInt(hex, 16));
                    index += 4;
                }
                default -> value.append(escaped);
            }
        }
        throw new IllegalArgumentException("unterminated json string");
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long numberValue(Object value) {
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    public record JwtClaims(String username, String roleCode, String userId, long expiresAt) {
    }
}

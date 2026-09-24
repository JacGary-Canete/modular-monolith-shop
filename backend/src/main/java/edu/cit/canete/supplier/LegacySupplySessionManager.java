package edu.cit.canete.supplier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import edu.cit.canete.supplier.xml.XmlUtil;

/**
 * Package-private. Handles getting and refreshing a LegacySupply session
 * token. Nothing outside this package ever sees a raw session token or
 * the auth endpoint.
 *
 * Sessions were measured (manually, via repeated calls) to last roughly
 * 90-120 seconds. We conservatively treat a session as stale after 60
 * seconds so we refresh before LegacySupply would reject it, rather than
 * reactively refreshing only after a 401.
 */
@Component
class LegacySupplySessionManager {

    private static final Duration ASSUMED_LIFETIME = Duration.ofSeconds(60);
    private static final String BASE_URL = "https://legacysupply.onrender.com/api/v1";

    private final HttpClient httpClient;
    private final String clientId;
    private final String apiKey;

    private volatile String currentToken;
    private volatile Instant issuedAt = Instant.EPOCH;

    LegacySupplySessionManager(
            @Value("${LS_CLIENT_ID:}") String clientId,
            @Value("${LS_API_KEY:}") String apiKey) {
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /** Returns a session token, refreshing first if the current one looks stale. */
    synchronized String getValidSession() {
        if (currentToken == null || Duration.between(issuedAt, Instant.now()).compareTo(ASSUMED_LIFETIME) > 0) {
            refresh();
        }
        return currentToken;
    }

    /** Forces a new session, used after LegacySupply rejects the current one mid-call. */
    synchronized String forceRefresh() {
        refresh();
        return currentToken;
    }

    private void refresh() {
        String body = "<AuthRequest><ClientId>" + XmlUtil.escape(clientId)
                + "</ClientId><ApiKey>" + XmlUtil.escape(apiKey) + "</ApiKey></AuthRequest>";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/auth/token"))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new SupplierUnavailableException(
                        "Auth failed with status " + response.statusCode() + ": " + response.body());
            }
            String token = XmlUtil.extractTag(response.body(), "SessionToken");
            if (token == null) {
                throw new SupplierUnavailableException("Auth response had no SessionToken: " + response.body());
            }
            this.currentToken = token;
            this.issuedAt = Instant.now();
        } catch (java.io.IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SupplierUnavailableException("Could not reach LegacySupply auth endpoint", e);
        }
    }
}
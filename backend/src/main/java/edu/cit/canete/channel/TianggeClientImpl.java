package edu.cit.canete.channel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import edu.cit.canete.AppInstance;
import edu.cit.canete.channel.json.CancellationConfirm;
import edu.cit.canete.channel.json.DecisionRequest;
import edu.cit.canete.channel.json.HeartbeatRequest;
import edu.cit.canete.channel.json.ListingJson;
import edu.cit.canete.channel.json.ResolutionRequest;
import edu.cit.canete.channel.json.StockUpdateJson;

@Service
class TianggeClientImpl implements TianggeGateway {

    private static final String BASE_URL = "https://legacysupply.onrender.com/tiangge/v1";

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final AppInstance appInstance;
    private final String clientId;
    private final String apiKey;

    TianggeClientImpl(
            AppInstance appInstance,
            @Value("${LS_CLIENT_ID:}") String clientId,
            @Value("${LS_API_KEY:}") String apiKey) {
        this.appInstance = appInstance;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .header("X-Client-Id", clientId)
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Client-Instance", appInstance.getInstanceId().toString());
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new ChannelUnavailableException("Could not serialize request", e);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new ChannelUnavailableException("Could not reach Tiangge: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendHeartbeat() {
        HeartbeatRequest body = new HeartbeatRequest(
                "modular-monolith-shop",
                appInstance.getStartedAt().toString(),
                appInstance.getUptimeSeconds());

        HttpRequest request = baseRequest("/instances/heartbeat")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();

        HttpResponse<String> response = sendWithRetry(request, 3);
        if (response.statusCode() != 200) {
            throw new ChannelUnavailableException(
                    "Heartbeat failed with status " + response.statusCode() + ": " + response.body());
        }
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request, int maxAttempts) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 503) {
                    lastError = new ChannelUnavailableException(
                            "503 on attempt " + attempt + ": " + response.body());
                    if (attempt < maxAttempts) {
                        sleep(attempt);
                        continue;
                    }
                    throw (ChannelUnavailableException) lastError;
                }
                return response; // any non-503 response, success or a real error, is returned as-is
            } catch (java.io.IOException | InterruptedException e) {
                lastError = e;
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (attempt < maxAttempts) {
                    sleep(attempt);
                    continue;
                }
            }
        }
        throw new ChannelUnavailableException(
                "Could not reach Tiangge after " + maxAttempts + " attempts: "
                        + (lastError != null ? lastError.getMessage() : "unknown"), lastError);
    }

    private void sleep(int attempt) {
        try {
            Thread.sleep(500L * attempt); // 500ms, 1000ms, 1500ms...
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void publishListings(List<Listing> listings) {
        List<ListingJson> body = listings.stream()
                .map(l -> new ListingJson(l.sellerSku(), l.title(), l.supplierSku()))
                .toList();

        HttpRequest request = baseRequest("/listings")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();

        HttpResponse<String> response = sendWithRetry(request, 3);
        if (response.statusCode() != 200 && response.statusCode() != 204) {
            throw new ChannelUnavailableException(
                    "Publish listings failed with status " + response.statusCode() + ": " + response.body());
        }
    }

    @Override
    public void publishStock(String sellerSku, int available) {
        List<StockUpdateJson> body = List.of(new StockUpdateJson(sellerSku, available));

        HttpRequest request = baseRequest("/stock")
                .PUT(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();

        HttpResponse<String> response = sendWithRetry(request, 3);
        if (response.statusCode() != 200 && response.statusCode() != 204) {
            throw new ChannelUnavailableException(
                    "Publish stock failed with status " + response.statusCode() + ": " + response.body());
        }
    }

    @Override
    public FeedPage pollFeed(long after, int limit) {
        HttpRequest request = baseRequest("/feed?after=" + after + "&limit=" + limit)
                .GET()
                .build();

        HttpResponse<String> response = sendWithRetry(request, 3);
        if (response.statusCode() != 200) {
            throw new ChannelUnavailableException(
                    "Feed poll failed with status " + response.statusCode() + ": " + response.body());
        }

        try {
            return mapper.readValue(response.body(), FeedPage.class);
        } catch (Exception e) {
            throw new ChannelUnavailableException("Could not parse feed response", e);
        }
    }

    @Override
    public void decide(String tianggeOrderId, String decision, String shopOrderId, String reason) {
        DecisionRequest body = new DecisionRequest(decision, shopOrderId, reason);

        HttpRequest request = baseRequest("/orders/" + tianggeOrderId + "/decision")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();

        HttpResponse<String> response = sendWithRetry(request, 3);
        if (response.statusCode() != 200) {
            throw new ChannelUnavailableException(
                    "Decision failed with status " + response.statusCode() + ": " + response.body());
        }
    }

    @Override
    public void confirmCancellation(String tianggeOrderId) {
        CancellationConfirm body = new CancellationConfirm(true);

        HttpRequest request = baseRequest("/orders/" + tianggeOrderId + "/cancellation")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();

        HttpResponse<String> response = sendWithRetry(request, 3);
        if (response.statusCode() != 200) {
            throw new ChannelUnavailableException(
                    "Cancellation confirm failed with status " + response.statusCode() + ": " + response.body());
        }
    }

    @Override
    public void resolveBackorder(String tianggeOrderId, String status) {
        ResolutionRequest body = new ResolutionRequest(status);

        HttpRequest request = baseRequest("/orders/" + tianggeOrderId + "/resolution")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();

        HttpResponse<String> response = sendWithRetry(request, 3);
        if (response.statusCode() != 200) {
            throw new ChannelUnavailableException(
                    "Resolution failed with status " + response.statusCode() + ": " + response.body());
        }
    }
}
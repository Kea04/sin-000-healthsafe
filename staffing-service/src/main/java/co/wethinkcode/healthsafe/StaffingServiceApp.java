package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

public class StaffingServiceApp {

    private static final String WARD_SERVICE_URL = "http://localhost:7031/wards/";
    private static final String ALERT_LEVEL_URL = "http://localhost:7032/alert-level";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) {

        Javalin app = Javalin.create().start(7033);

        app.get("/health", ctx -> ctx.result("OK"));
        // TODO (Provides on-call schedules for doctors based on ward and status.)
        // Add domain endpoints for staffing-service here.

        // MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)


        app.post("/schedule/{wardId}", ctx -> {
            String wardId = ctx.pathParam("wardId").toUpperCase();

            HttpResponse<String> wardResponse = HTTP.send(
                    HttpRequest.newBuilder(URI.create(WARD_SERVICE_URL + wardId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (wardResponse.statusCode() == 404) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "unknown ward: " + wardId));
                return;
            }

            HttpResponse<String> alertResponse = HTTP.send(
                    HttpRequest.newBuilder(URI.create(ALERT_LEVEL_URL)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<?, ?> alertBody = MAPPER.readValue(alertResponse.body(), Map.class);
            int level = ((Number) alertBody.get("level")).intValue();

            int doctorsOnCall = 2 + level; // base on-call team, scaled by Emergency Status
            boolean surgeStaffing = level >= 6;

            Map<String, Object> schedule = Map.of(
                    "wardId", wardId,
                    "alertLevel", level,
                    "doctorsOnCall", doctorsOnCall,
                    "surgeStaffing", surgeStaffing,
                    "generatedAt", Instant.now().toString()
            );

            ctx.json(schedule);
        });
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)

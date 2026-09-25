package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import co.wethinkcode.healthsafe.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.util.concurrent.CopyOnWriteArrayList;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
// MQ TODO: publishes to ActiveMQ queue MqConfig.QUEUE when it detects an equipment failure on one of its wards.

public class WardServiceApp {

    private static final String INGESTION_URL = "http://localhost:7030/wards";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, WardRecord> WARDS_BY_ID = new ConcurrentHashMap<>();
    private static final List<String> STAFFING_EVENTS = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        loadWardsFromIngestion();

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(MqConfig.TOPIC);
        MessageConsumer consumer = session.createConsumer(topic);
        consumer.setMessageListener(message -> {
            try {
                if (message instanceof TextMessage textMessage) {
                    STAFFING_EVENTS.add(textMessage.getText());
                    System.out.println("ward-service received staffing event: " + textMessage.getText());
                }
            } catch (JMSException e) {
                System.err.println("Failed to read staffing event: " + e.getMessage());
            }
        });

        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", ctx -> ctx.json(List.copyOf(WARDS_BY_ID.values())));
        app.get("/staffing-events", ctx -> ctx.json(STAFFING_EVENTS));

        app.get("/wards/{id}", ctx -> {
            String id = ctx.pathParam("id").toUpperCase();
            WardRecord ward = WARDS_BY_ID.get(id);
            if (ward == null) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "unknown ward: " + id));
                return;
            }
            ctx.json(ward);
        });
    }

    private static void loadWardsFromIngestion() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(INGESTION_URL)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        WardRecord[] wards = MAPPER.readValue(response.body(), WardRecord[].class);
        for (WardRecord ward : wards) {
            WARDS_BY_ID.put(ward.wardId(), ward);
        }
        System.out.println("ward-service loaded " + WARDS_BY_ID.size() + " wards from ingestion-service.");
    }
}
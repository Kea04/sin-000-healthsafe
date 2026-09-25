package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
        import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class EquipmentAlertServiceApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<Map<?, ?>> ALERTS = new CopyOnWriteArrayList<>();

    // TODO (Uses a Queue to guarantee delivery of critical medical equipment failure alerts.)
    // Mechanism: ActiveMQ Queue (guaranteed delivery)

    // MQ TODO: consumes ActiveMQ queue MqConfig.QUEUE at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
    // Producer: ward-service publishes here when it detects an equipment failure on one of its wards.

    public static void main(String[] args) throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        Connection connection = factory.createConnection();
        connection.start();

        // CLIENT_ACKNOWLEDGE: the broker only removes a message once we explicitly
        // acknowledge it. If this service dies mid-processing, the message stays on
        // the queue for redelivery instead of being silently lost — this is the
        // "guaranteed delivery" this service exists for.
        Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue = session.createQueue(MqConfig.QUEUE);
        MessageConsumer consumer = session.createConsumer(queue);

        consumer.setMessageListener(message -> {
            try {
                if (message instanceof TextMessage textMessage) {
                    Map<?, ?> alert = MAPPER.readValue(textMessage.getText(), Map.class);
                    ALERTS.add(alert);
                    System.out.println("EQUIPMENT FAILURE ALERT: " + textMessage.getText());
                }
                message.acknowledge();
            } catch (Exception e) {
                // Deliberately don't acknowledge — leaves it on the queue for redelivery.
                System.err.println("Failed to process equipment failure alert: " + e.getMessage());
            }
        });

        Javalin app = Javalin.create().start(7034);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/alerts", ctx -> ctx.json(ALERTS));
    }
}
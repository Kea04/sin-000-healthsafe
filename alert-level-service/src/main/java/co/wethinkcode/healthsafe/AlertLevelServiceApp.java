
package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AlertLevelServiceApp {

    private static final AtomicInteger LEVEL = new AtomicInteger(0);

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7032);

        app.get("/health", ctx -> ctx.result("OK"));
        // TODO (Tracks the hospital Emergency Status (0-8, 8 = full Code Blue).)
        // Add domain endpoints for alert-level-service here.

        app.get("/alert-level", ctx -> ctx.json(Map.of("level", LEVEL.get())));

        app.post("/alert-level", ctx -> {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            Object rawLevel = body.get("level");
            if (!(rawLevel instanceof Integer level) || level < 0 || level > 8) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "level must be an integer 0-8"));
                return;
            }
            LEVEL.set(level);
            ctx.json(Map.of("level", LEVEL.get()));
        });
    }
}

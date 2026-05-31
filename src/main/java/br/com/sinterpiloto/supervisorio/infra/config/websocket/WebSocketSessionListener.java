package br.com.sinterpiloto.supervisorio.infra.config.websocket;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class WebSocketSessionListener {

    @Getter
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    @Getter
    private final AtomicInteger activeSubscriptions = new AtomicInteger(0);

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        int count = activeConnections.incrementAndGet();
        log.info("WebSocket conectado — sessão: {} | total: {}",
                getSessionId(event.getMessage().getHeaders()), count);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        int count = activeConnections.decrementAndGet();
        log.info("WebSocket desconectado — sessão: {} | total: {}",
                event.getSessionId(), count);
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        int count = activeSubscriptions.incrementAndGet();
        log.debug("Nova subscrição: {} | total: {}", accessor.getDestination(), count);
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        activeSubscriptions.decrementAndGet();
    }

    public boolean hasActiveSubscribers() {
        return activeConnections.get() > 0;
    }

    private String getSessionId(org.springframework.messaging.MessageHeaders headers) {
        Object sessionId = headers.get("simpSessionId");
        return sessionId != null ? sessionId.toString() : "unknown";
    }
}
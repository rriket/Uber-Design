package com.uberclone.notification.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple WebSocket handler that keeps a map of driverId -> session.
 * The driver client connects to  ws://.../ws/driver?driverId=xyz
 * and receives JSON payloads (ride requests, cancellations, ...).
 *
 * In production this would use STOMP + broker + FCM/APNs for offline devices.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String driverId = extractDriverId(session);
        if (driverId != null) {
            sessions.put(driverId, session);
            log.info("Driver {} connected via WS", driverId);
        } else {
            log.warn("WS connection missing driverId query param, closing.");
            try { session.close(CloseStatus.BAD_DATA); } catch (Exception ignored) {}
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.entrySet().removeIf(e -> e.getValue().getId().equals(session.getId()));
        log.info("WS session {} closed ({})", session.getId(), status);
    }

    public boolean send(String driverId, Object payload) {
        WebSocketSession s = sessions.get(driverId);
        if (s == null || !s.isOpen()) {
            log.info("No open WS session for driver {}", driverId);
            return false;
        }
        try {
            s.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
            return true;
        } catch (Exception e) {
            log.warn("Failed to push to driver {}: {}", driverId, e.getMessage());
            return false;
        }
    }

    public void broadcast(Object payload) {
        sessions.forEach((id, s) -> {
            if (s.isOpen()) {
                try { s.sendMessage(new TextMessage(mapper.writeValueAsString(payload))); }
                catch (Exception ignored) {}
            }
        });
    }

    private String extractDriverId(WebSocketSession session) {
        var uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;
        for (String kv : uri.getQuery().split("&")) {
            String[] p = kv.split("=", 2);
            if (p.length == 2 && "driverId".equals(p[0])) return p[1];
        }
        return null;
    }
}

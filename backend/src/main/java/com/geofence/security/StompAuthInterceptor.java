package com.geofence.security;

import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP CONNECT frames by reading the JWT from the "login" header.
 *
 * Why "login" header and not a query param?
 * Query params appear in access logs and browser history — a JWT in the URL is
 * a credential leak. The STOMP CONNECT "login" header is only in the message body,
 * which is not logged by default.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthInterceptor.class);
    private final JwtService jwtService;

    public StompAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String token = accessor.getLogin();
        if (token == null || token.isBlank()) {
            throw new MessageDeliveryException(message, "Authentication required");
        }

        try {
            JwtService.JwtDetails details = jwtService.extractDetails(token);
            JwtUserDetails principal = new JwtUserDetails(details.userId(), details.email());
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            accessor.setUser(auth);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("STOMP CONNECT rejected — invalid JWT: {}", e.getMessage());
            throw new MessageDeliveryException(message, "Authentication required");
        }

        return message;
    }
}

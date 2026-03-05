package com.geofence.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
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
            // No token — Spring Security will reject the session when a protected
            // destination is accessed. Returning the message here avoids breaking
            // the CONNECT entirely for unauthenticated contexts.
            return message;
        }

        try {
            JwtService.JwtDetails details = jwtService.extractDetails(token);
            JwtUserDetails principal = new JwtUserDetails(details.userId(), details.email());
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            accessor.setUser(auth);
        } catch (Exception e) {
            // Invalid token — leave user unset; access to protected destinations will fail
        }

        return message;
    }
}

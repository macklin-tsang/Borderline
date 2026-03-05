package com.geofence.service;

import com.geofence.dto.response.GeofenceAlertMessage;
import com.geofence.model.Device;
import com.geofence.model.Geofence;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AlertService {

    private final SimpMessagingTemplate messagingTemplate;

    public AlertService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sends a geofence crossing alert to the device owner's private queue.
     * Uses /user/{userId}/queue/alerts — NOT /topic/ to prevent cross-user data leakage.
     */
    public void sendAlert(String userId, Device device, Geofence fence, String type) {
        GeofenceAlertMessage alert = new GeofenceAlertMessage(
                type,
                device.getId(),
                device.getName(),
                fence.getId(),
                fence.getName(),
                Instant.now()
        );
        // SimpMessagingTemplate.convertAndSendToUser routes to /user/{userId}/queue/alerts
        messagingTemplate.convertAndSendToUser(userId, "/queue/alerts", alert);
    }
}

package com.chellavignesh.authserver.notification;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NotificationService {

    private final Set<NotificationChannel> channels;

    @Autowired
    public NotificationService(Set<NotificationChannel> channels) {
        this.channels = channels;

        final var channelNames = channels.stream()
                .map(NotificationChannel::getName)
                .collect(Collectors.joining(", "));

        log.info("Initialized service, available channels are: {}", channelNames);
    }

    public void send(
            @NotNull final NotificationContext context,
            @NotNull final BiConsumer<NotificationChannel, NotificationContext> consumer
    ) {
        channels.forEach(channel -> consumer.accept(channel, context));
    }
}

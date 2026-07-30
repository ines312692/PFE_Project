package com.elyadata.feature.job.infrastructure.stream;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.util.UUID;


@Slf4j
@Getter
@ApplicationScoped
public class ConsumerIdentity {

    private final String name;

    public ConsumerIdentity() {
        this.name = buildName();
        log.info("[STREAM] Consumer identity = {}", name);
    }

    private static String buildName() {
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) {
            host = "local";
        }
        String pid = ManagementFactory.getRuntimeMXBean().getName();
        String pidPart = pid.contains("@") ? pid.substring(0, pid.indexOf('@')) : pid;
        String rand = UUID.randomUUID().toString().substring(0, 8);
        return host + "-" + pidPart + "-" + rand;
    }
}
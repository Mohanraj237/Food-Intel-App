package com.familyfood.platform.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback converter that runs {@link SensitiveDataMasker} over every formatted message
 * on its way to an appender. Registered as the {@code maskedMsg} conversion word in
 * {@code logback-spring.xml}.
 *
 * <p>This is the last line of defence, not the first. The first is simply not logging
 * bodies or profile objects. But a masker that only works when a developer remembers to
 * call it is not a control, so it sits in the appender path where it cannot be forgotten.
 *
 * <p>The pepper is read from the environment directly rather than injected: Logback
 * starts before the Spring context and this converter cannot participate in DI. A missing
 * pepper degrades to a fixed development value — safe, because the pepper only affects
 * the reversibility of pseudonyms in logs, and production always supplies one.
 */
public class MaskingMessageConverter extends MessageConverter {

    private static final String DEV_FALLBACK_PEPPER = "local-development-pepper";

    private final SensitiveDataMasker masker;

    public MaskingMessageConverter() {
        String pepper = System.getenv("FAMILYFOOD_PRIVACY_HASH_PEPPER");
        if (pepper == null || pepper.isBlank()) {
            pepper = System.getProperty("familyfood.privacy.hash-pepper", DEV_FALLBACK_PEPPER);
        }
        this.masker = new SensitiveDataMasker(pepper);
    }

    @Override
    public String convert(ILoggingEvent event) {
        String formatted = super.convert(event);
        try {
            return masker.maskText(formatted);
        } catch (RuntimeException e) {
            // Losing a log line is worse than an unmasked one only if we also lose the
            // signal that masking broke. Emit a marker rather than the raw message.
            return "[masking-failed] " + event.getLoggerName();
        }
    }
}

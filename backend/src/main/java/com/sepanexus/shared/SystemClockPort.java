package com.sepanexus.shared;

import java.time.Instant;
import org.springframework.stereotype.Component;

/** The only class allowed to call {@link Instant#now()} directly — enforced by ArchUnit. */
@Component
public class SystemClockPort implements ClockPort {

    @Override
    public Instant now() {
        return Instant.now();
    }
}

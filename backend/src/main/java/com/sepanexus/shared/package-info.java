/**
 * Shared-kernel module (EPIC-22 Story 22.3): infrastructure ports with no dependency on any other
 * module, consumed by {@code modules} (and, later, every other domain module) for cross-cutting
 * concerns like the system clock.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package com.sepanexus.shared;

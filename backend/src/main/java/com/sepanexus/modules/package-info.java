/**
 * Payment lifecycle module (EPIC-09 Story 9.3). Allowed to depend on {@code shared} (EPIC-22
 * Story 22.3 — {@code ClockPort}) — extend this list further only as later ownership epics
 * (EPIC-10+) introduce real ports this module needs (e.g. iso-adapter, reference-data).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared"})
package com.sepanexus.modules;

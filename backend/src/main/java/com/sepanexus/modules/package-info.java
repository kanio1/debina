/**
 * Payment lifecycle module (EPIC-09 Story 9.3). No allowed dependencies today because no
 * other module's port exists yet to depend on — extend this list only as later ownership
 * epics (EPIC-10+) introduce real ports this module needs (e.g. iso-adapter, reference-data).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package com.sepanexus.modules;

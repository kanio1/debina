/**
 * Identity/security cross-cutting module (EPIC-09 Story 9.3). No allowed dependencies
 * today — this module authenticates and authorizes; it does not read or write any
 * domain module's state.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package com.sepanexus.security;

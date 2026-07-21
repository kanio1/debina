/**
 * Evidence-audit module: the sole owner of application evidence/audit persistence. Its public API
 * exposes typed ports and value objects only; repositories, SQL and SECURITY DEFINER wiring remain
 * internal. Keycloak login/admin events remain in Keycloak's separate audit plane.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package com.sepanexus.evidenceaudit;

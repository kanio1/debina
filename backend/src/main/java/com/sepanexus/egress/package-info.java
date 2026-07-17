/**
 * Egress module (EPIC-43, sepa-nexus-message-flow-and-data-blueprint.md §3.11/§6): owns transport
 * state only, never finality — delivering or confirming a status message is transport state, not
 * settlement finality, and this module must never write {@code payments.status} or
 * {@code settlement_*}. This slice ({@link com.sepanexus.egress.internal.OutboundMessageDispatcher},
 * EPIC-43 Story 43.1) only claims pending rows from {@code egress.outbound_messages}
 * (transactional {@code FOR UPDATE SKIP LOCKED}, horizontally safe against concurrent
 * dispatchers) and advances their state to {@code CLAIMED_FOR_DELIVERY} — it never renders,
 * signs, or delivers an artifact; those are later stories' scope (43.2-43.5). Internals
 * ({@code .internal}) are not part of the module's public API.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package com.sepanexus.egress;

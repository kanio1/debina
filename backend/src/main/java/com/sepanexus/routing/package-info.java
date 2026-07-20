/**
 * Routing consumes static reference-data through narrow read ports and records decisions only in
 * its own schema in later pipeline stages. It never settles, books, changes payment state, or
 * establishes finality.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package com.sepanexus.routing;

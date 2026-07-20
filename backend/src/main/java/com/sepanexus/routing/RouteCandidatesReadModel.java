package com.sepanexus.routing;

import java.util.List;

/** Module-owned, read-only projection of active route candidates; it exposes no payment mutation. */
public interface RouteCandidatesReadModel {
    List<RouteCandidate> findCandidates(RouteCandidatesQuery query);
}

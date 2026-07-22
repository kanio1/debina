package main

import "dagger/debina-verification/internal/dagger"

const fastBackendTests = "ModularityTest,OwnershipArchRulesTest,PaymentNoGodModuleTest,ClockPortEnforcementTest,RuntimeDatasourceOwnershipTest,SchedulerActivationGuardTest"

const backendTestsWithoutTestcontainers = "com.sepanexus.ClockPortEnforcementTest,com.sepanexus.ModularityTest,com.sepanexus.OwnershipArchRulesTest,com.sepanexus.PaymentNoGodModuleTest,com.sepanexus.RuntimeDatasourceOwnershipTest,com.sepanexus.SchedulerActivationGuardTest,com.sepanexus.egress.internal.artifact.ArtifactTriggerMapTest,com.sepanexus.epic10.IsoAdapterNoBusinessDecisionTest,com.sepanexus.evidenceaudit.AuditQueryArchitectureTest,com.sepanexus.evidenceaudit.CommandAuditArchitectureTest,com.sepanexus.graphql.GraphQLReadModelOwnershipTest,com.sepanexus.graphql.GraphQLReadOnlyStructureTest,com.sepanexus.graphql.PaymentLineageGraphQLTest,com.sepanexus.ledger.LedgerPortReservationProtocolContractTest,com.sepanexus.modules.paymentlifecycle.domain.PaymentFsmTransitionTest,com.sepanexus.modules.paymentlifecycle.event.KafkaTopicContractTest,com.sepanexus.modules.paymentlifecycle.event.OutboxRelayAcknowledgementOrderTest,com.sepanexus.modules.paymentlifecycle.event.PaymentOutboxEventTypeTest,com.sepanexus.modules.paymentlifecycle.event.ScheduledRelayOperationalTruthTest,com.sepanexus.modules.paymentlifecycle.ingress.XmlHardeningTest,com.sepanexus.modules.paymentlifecycle.isoadapter.Pacs002CorrelationInputTest,com.sepanexus.modules.paymentlifecycle.isoadapter.Pacs002IdentifierExtractionTest,com.sepanexus.modules.paymentlifecycle.isoadapter.Pain001CanonicalMapperTest,com.sepanexus.modules.paymentlifecycle.service.PaymentServiceTest,com.sepanexus.modules.paymentlifecycle.service.TerminalBusinessStatusDoesNotImplyFinalityTest,com.sepanexus.routing.FallbackSelectedOutcomeTest,com.sepanexus.routing.NoImplicitInstantToBatchFallbackTest,com.sepanexus.routing.RouteCandidateResolutionTest,com.sepanexus.routing.RouteCandidatesReadModelTest,com.sepanexus.routing.RoutingFixturesTest,com.sepanexus.routing.RoutingPairwiseProfileTest,com.sepanexus.settlement.NetDeferredStrategyTest,com.sepanexus.settlement.NoProfileNameSwitchTest,com.sepanexus.settlement.SettlementStrategyResolverTest,com.sepanexus.signature.SignatureNoForeignRepoAccessTest"

func (m *DebinaVerification) backendFast() *dagger.Container {
	return dag.Container().
		From(javaImage).
		WithMountedCache("/root/.m2/repository", dag.CacheVolume("debina-maven-jdk25")).
		WithDirectory("/workspace", m.source()).
		WithWorkdir("/workspace").
		WithExec([]string{"./mvnw", "-f", "backend", "-DskipTests", "compile"}).
		WithExec([]string{"./mvnw", "-f", "backend", "test", "-Dtest=" + fastBackendTests})
}

// backendWithoutTestcontainers runs the complete current Maven subset that has
// no Testcontainers dependency. It is intentionally explicit so a new runtime
// test is never accidentally treated as socket-free.
func (m *DebinaVerification) backendWithoutTestcontainers() *dagger.Container {
	return dag.Container().
		From(javaImage).
		WithMountedCache("/root/.m2/repository", dag.CacheVolume("debina-maven-jdk25")).
		WithDirectory("/workspace", m.source()).
		WithWorkdir("/workspace").
		WithExec([]string{"./mvnw", "-f", "backend", "-Dtest=" + backendTestsWithoutTestcontainers, "test"})
}

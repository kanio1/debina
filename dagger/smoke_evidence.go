package main

import "dagger/debina-verification/internal/dagger"

const (
	d3BPaymentIDPath            = "/tmp/phase-d-payment-id"
	d3BJsonDirectEvidencePath   = "/tmp/phase-d-json-direct-evidence"
	d3BApprovalRulePath         = "/tmp/phase-d-approval-rule"
	d3BMakerCheckerEvidencePath = "/tmp/phase-d-maker-checker-evidence"
	d3BDetailEvidencePath       = "/tmp/phase-d-detail-lineage-evidence"
	d3BJsonDirectMarker         = "PHASE-D JSON_DIRECT EVIDENCE VERIFIED"
)

func (m *DebinaVerification) seedApprovalRule(rt *paymentSmokeRuntime) *dagger.File {
	return postgresClient(rt.postgres).
		WithFile("/tmp/phase-d-flyway-complete", rt.migrationMarker).
		WithEnvVariable("PGUSER", "reference_data_role").
		WithSecretVariable("PGPASSWORD", rt.credentials.referenceDataPassword).
		WithExec([]string{"sh", "-ec", `
set -eu
psql -v ON_ERROR_STOP=1 -c "BEGIN; SELECT set_config('app.tenant_id', '00000000-0000-0000-0000-000000000001', true); INSERT INTO reference_data.approval_matrix_rules (tenant_id, requires_approval, requires_step_up, valid_from) VALUES ('00000000-0000-0000-0000-000000000001', true, false, CURRENT_DATE); COMMIT;"
printf '%s\n' 'PHASE-D approval matrix fixture verified' > /tmp/phase-d-approval-rule
`}).
		File(d3BApprovalRulePath)
}

func (m *DebinaVerification) makerCheckerPostgresEvidence(rt *paymentSmokeRuntime, browserPaymentID *dagger.File) *dagger.File {
	return postgresClient(rt.postgres).
		WithFile(d3BPaymentIDPath, browserPaymentID).
		WithEnvVariable("PGUSER", "sepa_migration").
		WithSecretVariable("PGPASSWORD", rt.credentials.migrationPassword).
		WithExec([]string{"sh", "-ec", `
set -eu
payment_id="$(cat /tmp/phase-d-payment-id)"
printf '%s' "$payment_id" | grep -Ex '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' >/dev/null
psql -v ON_ERROR_STOP=1 -v payment_id="$payment_id" -Atqc '
SELECT count(*) = 1
FROM payment.payments p
JOIN payment.payment_approvals a ON a.payment_id = p.id
JOIN payment.outbox_events o ON o.aggregate_id = p.id
JOIN audit.audit_log al ON al.payment_id = p.id
WHERE p.id = :'"'"'payment_id'"'"'::uuid
  AND p.status = '"'"'RECEIVED'"'"'
  AND a.status = '"'"'APPROVED'"'"'
  AND a.checker_user_id IS NOT NULL
  AND a.checker_user_id <> a.maker_user_id
  AND a.decided_at IS NOT NULL
  AND o.event_type = '"'"'payment.received.v1'"'"'
  AND o.published_at IS NOT NULL
  AND al.command_type = '"'"'PAYMENT_APPROVAL_APPROVED'"'"'
  AND al.outcome = '"'"'SUCCESS'"'"'
  AND al.after_state ->> '"'"'approvalStatus'"'"' = '"'"'APPROVED'"'"'' | grep -x t
printf '%s\n' 'PHASE-D maker-checker PostgreSQL evidence verified' > /tmp/phase-d-maker-checker-evidence
`}).
		File(d3BMakerCheckerEvidencePath)
}

func (m *DebinaVerification) makerCheckerKafkaEvidence(rt *paymentSmokeRuntime, browserPaymentID, postgresEvidence *dagger.File) *dagger.Container {
	return dag.Container().
		From(kafkaImage).
		WithServiceBinding(kafkaServiceAlias, rt.kafka).
		WithFile(d3BPaymentIDPath, browserPaymentID).
		WithFile(d3BMakerCheckerEvidencePath, postgresEvidence).
		WithExec([]string{"sh", "-ec", `
set -eu
test "$(cat /tmp/phase-d-maker-checker-evidence)" = 'PHASE-D maker-checker PostgreSQL evidence verified'
payment_id="$(cat /tmp/phase-d-payment-id)"
/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic payment.received --from-beginning --max-messages 20 --timeout-ms 30000 \
  | grep -F "\"paymentId\":\"$payment_id\"" >/dev/null
printf '%s\n' 'PHASE-D MAKER-CHECKER EVIDENCE VERIFIED'
`})
}

func (m *DebinaVerification) paymentDetailPostgresEvidence(rt *paymentSmokeRuntime, browserPaymentID *dagger.File) *dagger.File {
	return postgresClient(rt.postgres).
		WithFile(d3BPaymentIDPath, browserPaymentID).
		WithEnvVariable("PGUSER", "sepa_migration").
		WithSecretVariable("PGPASSWORD", rt.credentials.migrationPassword).
		WithExec([]string{"sh", "-ec", `
set -eu
payment_id="$(cat /tmp/phase-d-payment-id)"
printf '%s' "$payment_id" | grep -Ex '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' >/dev/null
psql -v ON_ERROR_STOP=1 -v payment_id="$payment_id" -Atqc '
SELECT count(*) = 1
FROM payment.payments p
JOIN iso.payment_iso_identifiers i ON i.payment_id = p.id
JOIN iso.iso_messages m ON m.id = i.iso_message_id
JOIN iso.message_lineage l ON l.payment_id = p.id AND l.iso_message_id = m.id
JOIN payment.payment_status_history h ON h.payment_id = p.id
JOIN payment.payment_events e ON e.id = h.event_ref AND e.payment_id = p.id
JOIN payment.outbox_events o ON o.aggregate_id = p.id
WHERE p.id = :'"'"'payment_id'"'"'::uuid
  AND p.status = '"'"'RECEIVED'"'"'
  AND i.source_message_type = '"'"'JSON_DIRECT'"'"'
  AND i.end_to_end_id = '"'"'D3B-DETAIL-LINEAGE-0001'"'"'
  AND m.message_type = '"'"'JSON_DIRECT'"'"'
  AND h.seq = 1
  AND h.to_status = '"'"'RECEIVED'"'"'
  AND h.event_type = '"'"'payment.received.v1'"'"'
  AND e.type = '"'"'payment.received.v1'"'"'
  AND o.event_type = '"'"'payment.received.v1'"'"'
  AND o.published_at IS NOT NULL' | grep -x t
printf '%s\n' 'PHASE-D detail lineage PostgreSQL evidence verified' > /tmp/phase-d-detail-lineage-evidence
`}).
		File(d3BDetailEvidencePath)
}

func (m *DebinaVerification) paymentDetailKafkaEvidence(rt *paymentSmokeRuntime, browserPaymentID, postgresEvidence *dagger.File) *dagger.Container {
	return dag.Container().
		From(kafkaImage).
		WithServiceBinding(kafkaServiceAlias, rt.kafka).
		WithFile(d3BPaymentIDPath, browserPaymentID).
		WithFile(d3BDetailEvidencePath, postgresEvidence).
		WithExec([]string{"sh", "-ec", `
set -eu
test "$(cat /tmp/phase-d-detail-lineage-evidence)" = 'PHASE-D detail lineage PostgreSQL evidence verified'
payment_id="$(cat /tmp/phase-d-payment-id)"
/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic payment.received --from-beginning --max-messages 20 --timeout-ms 30000 \
  | grep -F "\"paymentId\":\"$payment_id\"" >/dev/null
printf '%s\n' 'PHASE-D DETAIL-LINEAGE EVIDENCE VERIFIED'
`})
}

// jsonDirectPostgresEvidence consumes only the payment UUID emitted by the
// browser journey. It checks source-owned persistence and lineage after the
// browser has completed; no browser state, token, or cookie crosses this edge.
func (m *DebinaVerification) jsonDirectPostgresEvidence(rt *paymentSmokeRuntime, browserPaymentID *dagger.File) *dagger.File {
	return postgresClient(rt.postgres).
		WithFile(d3BPaymentIDPath, browserPaymentID).
		WithFile("/tmp/phase-d-flyway-complete", rt.migrationMarker).
		WithEnvVariable("PGUSER", "sepa_migration").
		WithSecretVariable("PGPASSWORD", rt.credentials.migrationPassword).
		WithExec([]string{"sh", "-ec", `
set -eu
payment_id="$(cat /tmp/phase-d-payment-id)"
printf '%s' "$payment_id" | grep -Ex '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' >/dev/null
psql -v ON_ERROR_STOP=1 -v payment_id="$payment_id" -Atqc '
SELECT count(*) = 1
FROM payment.payments p
JOIN payment.payment_approvals a ON a.payment_id = p.id
JOIN iso.payment_iso_identifiers i ON i.payment_id = p.id
JOIN iso.iso_messages m ON m.id = i.iso_message_id
JOIN iso.message_lineage l ON l.payment_id = p.id AND l.iso_message_id = m.id
JOIN ingress.raw_inbound_messages r ON r.id = m.raw_message_id
WHERE p.id = :'"'"'payment_id'"'"'::uuid
  AND p.status = '"'"'RECEIVED'"'"'
  AND a.status = '"'"'NOT_REQUIRED'"'"'
  AND i.source_message_type = '"'"'JSON_DIRECT'"'"'
  AND i.end_to_end_id = '"'"'D3B-JSON-DIRECT-0001'"'"'
  AND m.direction = '"'"'INBOUND'"'"'
  AND m.message_type = '"'"'JSON_DIRECT'"'"'
  AND r.channel = '"'"'REST_JSON'"'"'' | grep -x t
psql -v ON_ERROR_STOP=1 -v payment_id="$payment_id" -Atqc '
SELECT count(*) = 1
FROM payment.outbox_events
WHERE aggregate_id = :'"'"'payment_id'"'"'::uuid
  AND event_type = '"'"'payment.received.v1'"'"'
  AND published_at IS NOT NULL' | grep -x t
printf '%s\n' 'PHASE-D JSON_DIRECT PostgreSQL evidence verified' > /tmp/phase-d-json-direct-evidence
`}).
		File(d3BJsonDirectEvidencePath)
}

func (m *DebinaVerification) jsonDirectKafkaEvidence(rt *paymentSmokeRuntime, browserPaymentID, postgresEvidence *dagger.File) *dagger.Container {
	return dag.Container().
		From(kafkaImage).
		WithServiceBinding(kafkaServiceAlias, rt.kafka).
		WithFile(d3BPaymentIDPath, browserPaymentID).
		WithFile(d3BJsonDirectEvidencePath, postgresEvidence).
		WithExec([]string{"sh", "-ec", `
set -eu
test "$(cat /tmp/phase-d-json-direct-evidence)" = 'PHASE-D JSON_DIRECT PostgreSQL evidence verified'
payment_id="$(cat /tmp/phase-d-payment-id)"
printf '%s' "$payment_id" | grep -Ex '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' >/dev/null
/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic payment.received --from-beginning --max-messages 20 --timeout-ms 30000 \
  | grep -F "\"paymentId\":\"$payment_id\"" >/dev/null
printf '%s\n' 'PHASE-D JSON_DIRECT EVIDENCE VERIFIED'
`})
}

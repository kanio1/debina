-- Forward-only correction for V20 and PaymentHistoryRecorder's former FSM-topology coupling.
-- Frozen architecture: business status is independent from settlement finality; REJECTED and
-- DISPATCHED are never final merely because their current thin FSM has no outgoing transition.
-- This preserves every history row and changes only the false boolean assertion. There is no
-- settlement-owned FinalityPolicy or finality record in this migration set, so no legitimate
-- finality writer can have produced a true value for either terminal business status.

UPDATE payment.payment_status_history
SET is_final = false
WHERE is_final = true
  AND to_status IN ('REJECTED', 'DISPATCHED');

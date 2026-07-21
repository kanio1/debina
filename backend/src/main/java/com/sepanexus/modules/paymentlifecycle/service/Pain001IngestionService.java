package com.sepanexus.modules.paymentlifecycle.service;

import com.sepanexus.modules.paymentlifecycle.ingress.HardenedXmlFactory;
import com.sepanexus.modules.paymentlifecycle.ingress.SignatureVerificationFailedException;
import com.sepanexus.modules.paymentlifecycle.ingress.SignedChannelIngestionPipeline;
import com.sepanexus.modules.paymentlifecycle.ingress.XmlHardeningRejectedException;
import com.sepanexus.modules.paymentlifecycle.isoadapter.CanonicalMapper;
import com.sepanexus.modules.paymentlifecycle.isoadapter.CanonicalMappingException;
import com.sepanexus.modules.paymentlifecycle.isoadapter.CanonicalMappingResult;
import com.sepanexus.modules.paymentlifecycle.isoadapter.IsoParseErrorRecorder;
import com.sepanexus.modules.paymentlifecycle.isoadapter.Pain001LineageRecorder;
import com.sepanexus.shared.ClockPort;
import com.sepanexus.signature.Verdict;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * EPIC-19 Story 19.4 — REST XML pain.001, the enforced pipeline order (§2.2/§2.5, G1):
 * {@code archive → verify → hardened parse → canonical map → domain idempotency → create payment →
 * ISO identifiers/lineage → outbox}. This method is deliberately NOT {@code @Transactional}: the
 * archive/verify stages ({@link SignedChannelIngestionPipeline}) must durably persist raw evidence
 * and the signature verdict even when a later stage (mapping, idempotency conflict) rejects the
 * message — only the payment-creation tail ({@link Pain001PersistenceService}) is one atomic unit.
 * The same reasoning applies to {@link IsoParseErrorRecorder}: it is called from this
 * non-transactional method, so its {@code INSERT} auto-commits immediately and survives whatever
 * happens afterward (throwing {@link XmlHardeningRejectedException} here never rolls it back —
 * there is no surrounding transaction to roll back).
 */
@Service
public class Pain001IngestionService {

    private static final String CHANNEL = "bank-xml";

    private final SignedChannelIngestionPipeline pipeline;
    private final CanonicalMapper canonicalMapper;
    private final Pain001PersistenceService persistenceService;
    private final IsoParseErrorRecorder isoParseErrorRecorder;
    private final ClockPort clockPort;

    public Pain001IngestionService(SignedChannelIngestionPipeline pipeline, CanonicalMapper canonicalMapper,
            Pain001PersistenceService persistenceService, IsoParseErrorRecorder isoParseErrorRecorder,
            ClockPort clockPort) {
        this.pipeline = pipeline;
        this.canonicalMapper = canonicalMapper;
        this.persistenceService = persistenceService;
        this.isoParseErrorRecorder = isoParseErrorRecorder;
        this.clockPort = clockPort;
    }

    @PreAuthorize("hasRole('payment_submitter')")
    public PaymentSubmissionResult submit(Pain001SubmissionCommand command) {
        SignedChannelIngestionPipeline.PipelineResult result = pipeline.ingest(CHANNEL, command.tenantId(),
                Pain001LineageRecorder.MESSAGE_TYPE, command.xmlBytes(), command.signatureBytes(),
                command.declaredSignerId(), command.algo(), true, clockPort.now());

        if (result.verdict().result() == Verdict.Result.FAILED) {
            throw new SignatureVerificationFailedException(result.verdict().reasonCode());
        }

        HardenedXmlFactory.HardenedParseResult parseResult = result.parseResult();
        if (!parseResult.accepted()) {
            isoParseErrorRecorder.record(result.rawMessageId(), Pain001LineageRecorder.MESSAGE_TYPE,
                    XmlHardeningRejectedException.ERROR_CODE, null, parseResult.rejectionReason(), clockPort.now());
            throw new XmlHardeningRejectedException(parseResult.rejectionReason());
        }

        CanonicalMappingResult mapping = canonicalMapper.map(parseResult.document());
        if (!mapping.success()) {
            throw new CanonicalMappingException(mapping.error());
        }

        return persistenceService.persist(command.tenantId(), command.branchId(), command.makerUserId(),
                command.idempotencyKey(), sha256(command.xmlBytes()), result.rawMessageId(), mapping.command());
    }

    private static byte[] sha256(byte[] payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

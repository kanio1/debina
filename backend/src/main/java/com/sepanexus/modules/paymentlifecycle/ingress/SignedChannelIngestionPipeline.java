package com.sepanexus.modules.paymentlifecycle.ingress;

import com.sepanexus.signature.SignatureVerificationPort;
import com.sepanexus.signature.SignatureVerificationRequest;
import com.sepanexus.signature.Verdict;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * EPIC-19 Story 19.2: the enforced ordering for signed/XML channels — {@code archive → verify →
 * parse}, never any other order (G1, signature-before-parse). A {@code FAILED} verdict stops the
 * pipeline before {@link HardenedXmlFactory#parse} ever runs; the raw bytes are archived
 * regardless of the verdict, so a rejected message is still evidence. This does not map the parsed
 * document to a canonical payment command — that mapping ({@code CanonicalMapper}) is Story 19.4,
 * still blocked; this pipeline owns only the ordering guarantee.
 */
@Component
public class SignedChannelIngestionPipeline {

    private final RawMessageArchive rawMessageArchive;
    private final SignatureVerificationPort signaturePort;
    private final HardenedXmlFactory hardenedXmlFactory;

    public SignedChannelIngestionPipeline(RawMessageArchive rawMessageArchive, SignatureVerificationPort signaturePort,
            HardenedXmlFactory hardenedXmlFactory) {
        this.rawMessageArchive = rawMessageArchive;
        this.signaturePort = signaturePort;
        this.hardenedXmlFactory = hardenedXmlFactory;
    }

    public PipelineResult ingest(String channel, UUID tenantId, String messageType, byte[] rawBytes,
            byte[] signatureBytes, UUID declaredSignerId, String algo, boolean signatureRequired, Instant asOf) {
        UUID rawMessageId = rawMessageArchive.archive(channel, tenantId, messageType, rawBytes);

        Verdict verdict = signaturePort.verify(new SignatureVerificationRequest(rawMessageId, rawBytes,
                signatureBytes, declaredSignerId, algo, channel, signatureRequired, asOf));

        if (verdict.result() == Verdict.Result.FAILED) {
            return PipelineResult.rejected(rawMessageId, verdict);
        }

        HardenedXmlFactory.HardenedParseResult parseResult = hardenedXmlFactory.parse(rawBytes);
        return PipelineResult.parsed(rawMessageId, verdict, parseResult);
    }

    public record PipelineResult(UUID rawMessageId, Verdict verdict,
            HardenedXmlFactory.HardenedParseResult parseResult) {

        static PipelineResult rejected(UUID rawMessageId, Verdict verdict) {
            return new PipelineResult(rawMessageId, verdict, null);
        }

        static PipelineResult parsed(UUID rawMessageId, Verdict verdict,
                HardenedXmlFactory.HardenedParseResult parseResult) {
            return new PipelineResult(rawMessageId, verdict, parseResult);
        }
    }
}

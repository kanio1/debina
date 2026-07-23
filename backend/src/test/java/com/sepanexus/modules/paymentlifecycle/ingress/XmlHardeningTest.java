package com.sepanexus.modules.paymentlifecycle.ingress;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("fast")
class XmlHardeningTest {

    private final HardenedXmlFactory factory = new HardenedXmlFactory();

    @Test
    void acceptsOrdinaryWellFormedXml() {
        String xml = "<?xml version=\"1.0\"?><Document><GrpHdr><MsgId>MSG-1</MsgId></GrpHdr></Document>";

        HardenedXmlFactory.HardenedParseResult result = factory.parse(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(result.accepted()).isTrue();
    }

    @Test
    void rejectsXxeExternalEntityPayload() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE Document [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <Document><GrpHdr><MsgId>&xxe;</MsgId></GrpHdr></Document>
                """;

        HardenedXmlFactory.HardenedParseResult result = factory.parse(xxe.getBytes(StandardCharsets.UTF_8));

        assertThat(result.accepted()).isFalse();
    }

    @Test
    void rejectsBillionLaughsEntityExpansionPayload() {
        String billionLaughs = """
                <?xml version="1.0"?>
                <!DOCTYPE lolz [
                  <!ENTITY lol "lol">
                  <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
                  <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
                ]>
                <lolz>&lol3;</lolz>
                """;

        HardenedXmlFactory.HardenedParseResult result = factory.parse(billionLaughs.getBytes(StandardCharsets.UTF_8));

        assertThat(result.accepted()).isFalse();
    }
}

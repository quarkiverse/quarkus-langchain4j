package io.quarkiverse.langchain4j.a2a.server.runtime.card;

import static io.quarkiverse.langchain4j.a2a.server.runtime.card.ConfigCardBuilderCustomizer.asUriHost;
import static io.quarkiverse.langchain4j.a2a.server.runtime.card.ConfigCardBuilderCustomizer.reachable;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The address a server is bound to is not always one a client can put in a URL, and the Agent Card exists to be
 * dialled.
 */
public class ConfigCardBuilderCustomizerTest {

    @Test
    public void testAFixedAddressIsUsedAsIs() {
        assertEquals("agents.example.com", reachable("agents.example.com"));
    }

    /**
     * The wildcard address is what Quarkus binds to outside dev and test mode. It names every interface the host
     * has rather than one a client can dial, so the card has to carry the host's own address in its place.
     */
    @Test
    public void testTheWildcardAddressIsReplacedByOneAClientCanDial() throws UnknownHostException {
        // the address the substitution is expected to reach for, named here rather than inferred from the result,
        // because a host whose own address is IPv6 would satisfy a test that only asked for the wildcard to be gone
        String hostAddress = InetAddress.getLocalHost().getHostAddress();

        for (String wildcard : List.of("0.0.0.0", "::", "::0", "0:0:0:0:0:0:0:0")) {
            assertEquals(hostAddress, reachable(wildcard));
        }
    }

    /**
     * The colons of an IPv6 literal would otherwise read as the start of the port.
     */
    @Test
    public void testAnIpv6AddressIsBracketed() {
        assertEquals("[::1]", asUriHost("::1"));
        assertEquals("[::1]", asUriHost("[::1]"));
    }

    @Test
    public void testAHostNameOrIpv4AddressIsLeftAlone() {
        assertEquals("agents.example.com", asUriHost("agents.example.com"));
        assertEquals("192.168.1.10", asUriHost("192.168.1.10"));
    }
}

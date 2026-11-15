package com.refill.auto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmsReceiverTest {
    private static final String LIDL_SMS =
            "Deine Unlimited-1GB Option ist fast verbraucht. Buche ein weiteres, kostenloses GB "
            + "über die Lidl Connect App, um die Reduzierung der Surfgeschwindigkeit zu verhindern. "
            + "Betätige hierzu den Refill-Button innerhalb der App, oder antworte einfach auf diese "
            + "SMS mit Refill. Dein Lidl Connect Team";

    private static boolean matchesDefaults(String sender, String body) {
        return SmsReceiver.matches(Prefs.DEFAULT_SENDER_FILTER, Prefs.DEFAULT_KEYWORD_FILTER, sender, body);
    }

    @Test public void lidlMessageMatchesFromAnySender() {
        assertTrue(matchesDefaults("80808", LIDL_SMS));
        assertTrue(matchesDefaults("+4980808", LIDL_SMS));
        assertTrue(matchesDefaults("Lidl Connect", LIDL_SMS));
    }

    @Test public void ordinaryMessagesDoNotMatch() {
        assertFalse(matchesDefaults("+4915112345678", "Kommst du heute Abend?"));
        assertFalse(matchesDefaults("80808", "Dein Guthaben wurde aufgeladen."));
        assertFalse(matchesDefaults("+4915112345678", "Refill"));
    }

    @Test public void bothFiltersBlankNeverMatches() {
        assertFalse(SmsReceiver.matches("", "", "80808", LIDL_SMS));
    }

    @Test public void senderFilterIgnoresPlusAndSpaces() {
        assertTrue(SmsReceiver.matches("80808", "", "+49 80808", "x"));
        assertFalse(SmsReceiver.matches("80808", "", "+4915112345678", "x"));
    }

    @Test public void replyGoesOnlyToRealNumbers() {
        assertTrue(SmsReceiver.isPhoneNumber("80808"));
        assertTrue(SmsReceiver.isPhoneNumber("+4980808"));
        assertFalse(SmsReceiver.isPhoneNumber("Lidl Connect"));
        assertFalse(SmsReceiver.isPhoneNumber(null));
    }
}

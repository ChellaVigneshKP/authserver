package com.chellavignesh.authserver.mfa.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FactorOptionTest {

    @Test
    void testConstructorAndGetters() {
        String value = "sms";
        String text = "SMS Authentication";
        
        FactorOption factorOption = new FactorOption(value, text);
        
        assertEquals(value, factorOption.getValue());
        assertEquals(text, factorOption.getText());
    }

    @Test
    void testSetters() {
        FactorOption factorOption = new FactorOption("old_value", "Old Text");
        
        factorOption.setValue("new_value");
        factorOption.setText("New Text");
        
        assertEquals("new_value", factorOption.getValue());
        assertEquals("New Text", factorOption.getText());
    }

    @Test
    void testEqualsAndHashCode() {
        FactorOption option1 = new FactorOption("email", "Email");
        FactorOption option2 = new FactorOption("email", "Email");
        FactorOption option3 = new FactorOption("sms", "SMS");
        
        assertEquals(option1, option2);
        assertEquals(option1.hashCode(), option2.hashCode());
        assertNotEquals(option1, option3);
    }

    @Test
    void testToString() {
        FactorOption factorOption = new FactorOption("totp", "TOTP Authenticator");
        
        String toString = factorOption.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("totp"));
        assertTrue(toString.contains("TOTP Authenticator"));
    }

    @Test
    void testNullValues() {
        FactorOption factorOption = new FactorOption(null, null);
        
        assertNull(factorOption.getValue());
        assertNull(factorOption.getText());
    }

    @Test
    void testEmptyStrings() {
        FactorOption factorOption = new FactorOption("", "");
        
        assertEquals("", factorOption.getValue());
        assertEquals("", factorOption.getText());
    }
}

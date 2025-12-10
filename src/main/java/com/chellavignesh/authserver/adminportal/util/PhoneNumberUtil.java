package com.chellavignesh.authserver.adminportal.util;

import org.apache.commons.lang3.StringUtils;

public final class PhoneNumberUtil {

    private PhoneNumberUtil() {
        /*
        Utility class
         */
    }

    public static String transformPhoneNumberToE164(String phoneNumber) {
        if (StringUtils.isEmpty(phoneNumber)) {
            return "";
        }

        String cleanedNumber = phoneNumber.replaceAll("[^\\d]", "");

        if (cleanedNumber.length() == 10) {
            return "+1" + cleanedNumber;
        } else if (cleanedNumber.length() == 11 && cleanedNumber.startsWith("1")) {
            return "+" + cleanedNumber;
        }

        return phoneNumber;
    }

    public static String transformE164ToPhoneNumber(String e164Number) {
        if (StringUtils.isEmpty(e164Number)) {
            return "";
        }

        String cleanedNumber = e164Number.startsWith("+") ? e164Number.substring(1) : e164Number;

        if (cleanedNumber.startsWith("1") && cleanedNumber.length() == 11) {
            return cleanedNumber.substring(1);
        } else if (!cleanedNumber.startsWith("1") && cleanedNumber.length() == 10) {
            return cleanedNumber;
        }

        return e164Number;
    }
}

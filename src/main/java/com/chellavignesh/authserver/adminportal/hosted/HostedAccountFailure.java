package com.chellavignesh.authserver.adminportal.hosted;

/** Only fixed, safe messages cross the hosted-form boundary; no rejected values or causes. */
public final class HostedAccountFailure extends RuntimeException {
    private final int status;

    public HostedAccountFailure(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static HostedAccountFailure invalid() {
        return new HostedAccountFailure(
                400,
                "This account action is invalid or expired. Return to your account and start"
                    + " again.");
    }

    public static HostedAccountFailure denied() {
        return new HostedAccountFailure(
                403,
                "This account does not match your current sign-in, or access is no longer"
                    + " available. Sign in again from your account.");
    }

    public static HostedAccountFailure unavailable() {
        return new HostedAccountFailure(
                503,
                "The account service is temporarily unavailable. Return to your account and try"
                    + " again.");
    }

    public static HostedAccountFailure managed() {
        return new HostedAccountFailure(
                403,
                "This account is managed by an external provider. Contact your administrator to"
                    + " change your profile or password.");
    }
}

package com.chellavignesh.authserver.adminportal.hosted;

public enum HostedAccountAction {
    PROFILE("profile"),
    PASSWORD("password");
    private final String wire;

    HostedAccountAction(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static HostedAccountAction parse(String value) {
        for (var action : values()) if (action.wire.equals(value)) return action;
        throw HostedAccountFailure.invalid();
    }
}

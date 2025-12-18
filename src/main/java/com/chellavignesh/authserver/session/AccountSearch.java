package com.chellavignesh.authserver.session;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccountSearch {

    private String branding;
    private String searchType;
    private Criteria criteria;

    @Getter
    @AllArgsConstructor
    public static class Criteria {
        private String username;
        private String email;
        private String last4SSN;
        private String accountNumber;
    }
}

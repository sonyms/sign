package com.adobe.sign.util;

import jakarta.servlet.http.HttpSession;

public class Util {

    public static void storeAgreementIdInSession(HttpSession session, String agreementId) {
        session.setAttribute("AGREEMENT_ID", agreementId);
    }

    // Method to retrieve agreementId from session
    public static String getAgreementIdFromSession(HttpSession session) {
        return (String) session.getAttribute("AGREEMENT_ID");
    }

}

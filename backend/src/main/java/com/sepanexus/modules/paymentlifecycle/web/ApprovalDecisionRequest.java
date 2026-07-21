package com.sepanexus.modules.paymentlifecycle.web;

/** Only the reject comment is request-controlled; identity comes from the validated JWT. */
public record ApprovalDecisionRequest(String decisionComment) { }

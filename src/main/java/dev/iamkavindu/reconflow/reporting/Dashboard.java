package dev.iamkavindu.reconflow.reporting;

public record Dashboard(long transactionsProcessed, long matched, long reviewRequired, long unmatched, long openCases, long resolvedCases) {}

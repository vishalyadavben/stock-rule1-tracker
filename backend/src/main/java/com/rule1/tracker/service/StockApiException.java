package com.rule1.tracker.service;

/** Thrown when Alpha Vantage (or whichever provider) returns something other than usable data —
 *  e.g. a rate-limit notice, an invalid API key message, or an unrecognized ticker. Surfacing
 *  this (instead of silently returning null, which is what was happening before) is what makes
 *  bug #1/#2 ("price won't fetch", "unrealized gain shows '-'") diagnosable from the UI. */
public class StockApiException extends RuntimeException {
    public StockApiException(String message) {
        super(message);
    }
}

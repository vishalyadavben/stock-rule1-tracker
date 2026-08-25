package com.rule1.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rule1.tracker.entity.BigFiveMetric;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Fetches live price + fundamentals from Alpha Vantage.
 * Swap this class (behind the same method signatures) if you move to
 * Financial Modeling Prep or another provider — nothing else in the app needs to change.
 *
 * NOTE: Alpha Vantage free tier = 25 requests/day (as of writing). Fine for personal use
 * with a handful of tickers refreshed periodically; not for many users hammering it live.
 * See README for caching guidance.
 */
@Service
public class StockDataService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${stock-api.api-key}")
    private String apiKey;

    @Value("${stock-api.base-url}")
    private String baseUrl;

    /**
     * Real-time (delayed ~15min on free tier) quote.
     * Throws StockApiException with a specific, useful message instead of silently returning
     * null — Alpha Vantage returns HTTP 200 even when it's rejecting the request (rate limit,
     * bad key, unrecognized ticker), just with a different JSON shape, so we have to inspect
     * the body to know whether it actually worked.
     */
    public BigDecimal fetchLatestPrice(String ticker) {
        String url = String.format("%s/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s", baseUrl, ticker, apiKey);
        JsonNode root;
        try {
            root = restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            throw new StockApiException("Could not reach Alpha Vantage: " + e.getMessage());
        }
        if (root == null) {
            throw new StockApiException("Alpha Vantage returned an empty response for " + ticker);
        }
        if (root.has("Note")) {
            throw new StockApiException("Alpha Vantage rate limit hit: " + root.get("Note").asText());
        }
        if (root.has("Information")) {
            // This is what you get back for an invalid/demo key or a malformed request —
            // commonly mistaken for "the ticker doesn't exist".
            throw new StockApiException("Alpha Vantage rejected the request: " + root.get("Information").asText());
        }
        JsonNode quote = root.path("Global Quote");
        String priceStr = quote.path("05. price").asText(null);
        if (priceStr == null || priceStr.isBlank()) {
            throw new StockApiException(
                    "No quote data returned for " + ticker + ". Raw response: " + root);
        }
        return new BigDecimal(priceStr);
    }

    /**
     * Pulls annual income statement, balance sheet, and cash flow reports and assembles
     * BigFiveMetric rows (one per fiscal year) with source = API.
     * stockId must be set by the caller after persisting.
     *
     * Alpha Vantage's free tier enforces "1 request per second" — this method makes 4 calls
     * (income, balance sheet, cash flow, overview), so we deliberately pace them out. This
     * doesn't fix the separate 25-requests/day cap, but it stops the "sometimes it works,
     * sometimes it doesn't" burst-limit rejections you'd otherwise see on every single refresh.
     */
    public List<BigFiveMetric> fetchBigFiveHistory(String ticker) {
        JsonNode income = callFunction("INCOME_STATEMENT", ticker).path("annualReports");
        pace();
        JsonNode balance = callFunction("BALANCE_SHEET", ticker).path("annualReports");
        pace();
        JsonNode cashFlow = callFunction("CASH_FLOW", ticker).path("annualReports");
        pace();
        JsonNode overview = callFunction("OVERVIEW", ticker);

        List<BigFiveMetric> results = new ArrayList<>();

        Iterator<JsonNode> incomeIt = income.elements();
        while (incomeIt.hasNext()) {
            JsonNode incRow = incomeIt.next();
            String fiscalDate = incRow.path("fiscalDateEnding").asText();
            int year = Integer.parseInt(fiscalDate.substring(0, 4));

            JsonNode balRow = findByYear(balance, year);
            JsonNode cfRow = findByYear(cashFlow, year);
            if (balRow == null || cfRow == null) continue;

            BigFiveMetric m = new BigFiveMetric();
            m.setFiscalYear(year);
            m.setSource(BigFiveMetric.Source.API);

            BigDecimal netIncome = decOrNull(incRow, "netIncome");
            BigDecimal totalEquity = decOrNull(balRow, "totalShareholderEquity");
            BigDecimal totalDebt = decOrNull(balRow, "longTermDebt");
            BigDecimal operatingCf = decOrNull(cfRow, "operatingCashflow");
            BigDecimal capex = decOrNull(cfRow, "capitalExpenditures");
            BigDecimal sharesOut = decOrNull(overview, "SharesOutstanding");

            m.setSales(decOrNull(incRow, "totalRevenue"));
            m.setEquity(totalEquity);
            m.setLongTermDebt(totalDebt);
            m.setSharesOut(sharesOut);

            if (netIncome != null && sharesOut != null && sharesOut.compareTo(BigDecimal.ZERO) > 0) {
                m.setEps(netIncome.divide(sharesOut, 4, java.math.RoundingMode.HALF_UP));
            }

            if (operatingCf != null && capex != null) {
                m.setFreeCashFlow(operatingCf.subtract(capex.abs()));
            }

            // ROIC approximation = Net Income / (Total Equity + Long Term Debt)
            if (netIncome != null && totalEquity != null && totalDebt != null) {
                BigDecimal investedCapital = totalEquity.add(totalDebt);
                if (investedCapital.compareTo(BigDecimal.ZERO) > 0) {
                    m.setRoicPct(netIncome
                            .divide(investedCapital, 6, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)));
                }
            }

            results.add(m);
        }
        return results;
    }

    private JsonNode findByYear(JsonNode reports, int year) {
        for (JsonNode r : reports) {
            String date = r.path("fiscalDateEnding").asText();
            if (date.startsWith(String.valueOf(year))) return r;
        }
        return null;
    }

    /** Simple pacing to stay under Alpha Vantage's free-tier "1 request per second" burst
     *  limit. A dedicated rate-limiter (e.g. Resilience4j) would be sturdier under concurrent
     *  users, but for personal/small-group use this is the pragmatic fix. */
    private void pace() {
        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Live FX rate between two currency codes (e.g. "USD" -> "INR"), via Alpha Vantage's
     *  CURRENCY_EXCHANGE_RATE endpoint — used to convert portfolio totals across currencies
     *  on request, rather than just warning that a mix exists. */
    public BigDecimal fetchExchangeRate(String from, String to) {
        String url = String.format("%s/query?function=CURRENCY_EXCHANGE_RATE&from_currency=%s&to_currency=%s&apikey=%s",
                baseUrl, from, to, apiKey);
        JsonNode root;
        try {
            root = restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            throw new StockApiException("Could not reach Alpha Vantage for exchange rate: " + e.getMessage());
        }
        if (root == null) throw new StockApiException("Alpha Vantage returned an empty exchange rate response");
        if (root.has("Note")) throw new StockApiException("Alpha Vantage rate limit hit: " + root.get("Note").asText());
        if (root.has("Information")) throw new StockApiException("Alpha Vantage rejected the request: " + root.get("Information").asText());

        JsonNode rateNode = root.path("Realtime Currency Exchange Rate").path("5. Exchange Rate");
        String rateStr = rateNode.asText(null);
        if (rateStr == null) {
            throw new StockApiException("No exchange rate returned for " + from + "->" + to + ". Raw response: " + root);
        }
        return new BigDecimal(rateStr);
    }

    private JsonNode callFunction(String function, String ticker) {
        String url = String.format("%s/query?function=%s&symbol=%s&apikey=%s", baseUrl, function, ticker, apiKey);
        JsonNode result;
        try {
            result = restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            throw new StockApiException("Could not reach Alpha Vantage for " + function + ": " + e.getMessage());
        }
        if (result == null) return mapper.createObjectNode();
        if (result.has("Note")) {
            throw new StockApiException("Alpha Vantage rate limit hit while fetching " + function + ": "
                    + result.get("Note").asText());
        }
        if (result.has("Information")) {
            throw new StockApiException("Alpha Vantage rejected " + function + " request: "
                    + result.get("Information").asText());
        }
        return result;
    }

    private BigDecimal decOrNull(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        if (v == null || v.equalsIgnoreCase("None")) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

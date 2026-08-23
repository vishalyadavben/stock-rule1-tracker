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

    /** Real-time (delayed ~15min on free tier) quote. */
    public BigDecimal fetchLatestPrice(String ticker) {
        String url = String.format("%s/query?function=GLOBAL_QUOTE&symbol=%s&apikey=%s", baseUrl, ticker, apiKey);
        JsonNode root = restTemplate.getForObject(url, JsonNode.class);
        if (root == null) return null;
        JsonNode quote = root.path("Global Quote");
        String priceStr = quote.path("05. price").asText(null);
        return priceStr == null ? null : new BigDecimal(priceStr);
    }

    /**
     * Pulls annual income statement, balance sheet, and cash flow reports and assembles
     * BigFiveMetric rows (one per fiscal year) with source = API.
     * stockId must be set by the caller after persisting.
     */
    public List<BigFiveMetric> fetchBigFiveHistory(String ticker) {
        JsonNode income = callFunction("INCOME_STATEMENT", ticker).path("annualReports");
        JsonNode balance = callFunction("BALANCE_SHEET", ticker).path("annualReports");
        JsonNode cashFlow = callFunction("CASH_FLOW", ticker).path("annualReports");
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

    private JsonNode callFunction(String function, String ticker) {
        String url = String.format("%s/query?function=%s&symbol=%s&apikey=%s", baseUrl, function, ticker, apiKey);
        JsonNode result = restTemplate.getForObject(url, JsonNode.class);
        return result == null ? mapper.createObjectNode() : result;
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

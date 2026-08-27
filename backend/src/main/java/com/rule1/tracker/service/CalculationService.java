package com.rule1.tracker.service;

import com.rule1.tracker.entity.BigFiveMetric;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Implements the core Rule #1 math from "Rule #1" by Phil Town:
 *  - CAGR growth rates for Sales / EPS / Equity / Free Cash Flow
 *  - ROIC pass/fail against the 10%-for-10-years bar
 *  - Sticker Price + Margin of Safety price
 *  - A 1-10 business quality score combining Big Five pass/fail + checklist completion
 */
@Service
public class CalculationService {

    private static final BigDecimal TEN_PERCENT = new BigDecimal("0.10");
    private static final MathContext MC = new MathContext(10);

    /**
     * Compound annual growth rate between two values over N years.
     * Handles negative/zero starting values gracefully (returns null — can't compute a
     * meaningful growth rate off a non-positive base, same as Phil Town's method, which
     * assumes positive numbers throughout).
     */
    public BigDecimal cagr(BigDecimal startValue, BigDecimal endValue, int years) {
        if (startValue == null || endValue == null || years <= 0) return null;
        if (startValue.compareTo(BigDecimal.ZERO) <= 0 || endValue.compareTo(BigDecimal.ZERO) <= 0) return null;

        double start = startValue.doubleValue();
        double end = endValue.doubleValue();
        double rate = Math.pow(end / start, 1.0 / years) - 1.0;
        return BigDecimal.valueOf(rate * 100).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Given a sorted (oldest -> newest) list of yearly Big Five rows, compute growth rates for
     * each of the requested year windows (e.g. 10, 5, 3, 1) — any window the user wants to see,
     * not just a fixed 10/5/1. If there isn't enough history for a given window (e.g. only 2
     * years of data but a 10-year window was requested), that window returns null — it must
     * NOT silently fall back to a shorter window while still labeling it "10yr", which would
     * misrepresent a 1-year change as a 10-year growth rate.
     */
    public Map<String, BigDecimal> computeGrowthRates(List<BigFiveMetric> yearlySorted, String field, int... windows) {
        int n = yearlySorted.size();
        Map<String, BigDecimal> result = new java.util.LinkedHashMap<>();
        for (int window : windows) {
            if (n - 1 < window) {
                result.put(window + "yr", null); // not enough history to honestly support this window
            } else {
                result.put(window + "yr", growthFor(yearlySorted, field, window));
            }
        }
        return result;
    }

    /** Backward-compatible default: the original 10/5/1-year windows. */
    public Map<String, BigDecimal> computeGrowthRates(List<BigFiveMetric> yearlySorted, String field) {
        return computeGrowthRates(yearlySorted, field, 10, 5, 1);
    }

    /**
     * Average ROIC over each requested year window — ROIC is already a yearly percentage, not
     * a cumulative value, so "growth" doesn't apply the same way it does to Sales/EPS/Equity/
     * FCF; what's useful instead is the mean over each window, shown the same way (10yr/5yr/
     * 3yr/1yr) so it lines up visually with the other four metrics.
     * Same honesty rule as computeGrowthRates: a "10yr" average requires 10 actual years of
     * data — it does not quietly average over however few years happen to exist.
     */
    public Map<String, BigDecimal> averageRoic(List<BigFiveMetric> yearlySorted, int... windows) {
        Map<String, BigDecimal> result = new java.util.LinkedHashMap<>();
        int n = yearlySorted.size();
        for (int window : windows) {
            if (n < window) {
                result.put(window + "yr", null);
                continue;
            }
            List<BigFiveMetric> lastN = yearlySorted.subList(n - window, n);
            List<BigDecimal> values = lastN.stream()
                    .map(BigFiveMetric::getRoicPct)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (values.isEmpty()) {
                result.put(window + "yr", null);
                continue;
            }
            BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(window + "yr", sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP));
        }
        return result;
    }

    private BigDecimal growthFor(List<BigFiveMetric> sorted, String field, int yearsBack) {
        int n = sorted.size();
        if (yearsBack <= 0 || yearsBack >= n) return null;
        BigFiveMetric startRow = sorted.get(n - 1 - yearsBack);
        BigFiveMetric endRow = sorted.get(n - 1);
        BigDecimal startVal = extract(startRow, field);
        BigDecimal endVal = extract(endRow, field);
        return cagr(startVal, endVal, yearsBack);
    }

    private BigDecimal extract(BigFiveMetric m, String field) {
        return switch (field) {
            case "sales" -> m.getSales();
            case "eps" -> m.getEps();
            case "equity" -> m.getEquity();
            case "freeCashFlow" -> m.getFreeCashFlow();
            default -> null;
        };
    }

    /** Pass/fail against the Rule #1 10%-per-year bar. Null input = unknown, not a pass. */
    public boolean passesTenPercentRule(BigDecimal growthPct) {
        return growthPct != null && growthPct.compareTo(BigDecimal.TEN) >= 0;
    }

    /**
     * Debt payoff years = longTermDebt / freeCashFlow. Rule #1 max acceptable = 3 years.
     */
    public BigDecimal debtPayoffYears(BigDecimal longTermDebt, BigDecimal freeCashFlow) {
        if (longTermDebt == null || freeCashFlow == null || freeCashFlow.compareTo(BigDecimal.ZERO) <= 0) return null;
        return longTermDebt.divide(freeCashFlow, 2, RoundingMode.HALF_UP);
    }

    /**
     * Sticker Price calculation, following the book's method exactly:
     *  1. futureEPS = currentEPS * (1 + growthRate)^10
     *  2. futurePrice = futureEPS * futurePE
     *  3. stickerPrice = futurePrice discounted back at the minimum acceptable rate of return over 10 years
     *  4. marginOfSafetyPrice = 50% of stickerPrice
     */
    public StickerPriceResult calculateStickerPrice(
            BigDecimal currentEps,
            BigDecimal estimatedGrowthPct,   // e.g. 15 for 15%
            BigDecimal estimatedFuturePe,
            BigDecimal minAcceptableReturnPct // e.g. 15 for 15%
    ) {
        double eps = currentEps.doubleValue();
        double g = estimatedGrowthPct.doubleValue() / 100.0;
        double pe = estimatedFuturePe.doubleValue();
        double mar = minAcceptableReturnPct.doubleValue() / 100.0;

        double futureEps = eps * Math.pow(1 + g, 10);
        double futurePrice = futureEps * pe;
        double stickerPrice = futurePrice / Math.pow(1 + mar, 10);
        double mosPrice = stickerPrice * 0.5;

        return new StickerPriceResult(
                round(futureEps), round(futurePrice), round(stickerPrice), round(mosPrice)
        );
    }

    /** Suggested default future PE = 2x growth rate, capped/compared against historical PE elsewhere in the UI. */
    public BigDecimal defaultFuturePe(BigDecimal estimatedGrowthPct) {
        return estimatedGrowthPct.multiply(BigDecimal.valueOf(2));
    }

    /**
     * Auto-fills the Sticker Price inputs straight from a stock's Big Five history, per the
     * book's method: use the latest EPS as the current EPS, and prioritize historical EQUITY
     * growth (not EPS growth) as the growth-rate estimate — falling back to the 5-yr window,
     * then a conservative 10% default, if 10-yr data isn't available. Future PE defaults to
     * 2x that growth rate. The result is a starting point the user can still edit before
     * calculating — never submitted automatically without their review.
     */
    public StickerSuggestion suggestStickerInputs(List<BigFiveMetric> yearlySorted) {
        if (yearlySorted == null || yearlySorted.isEmpty()) return null;
        BigFiveMetric latest = yearlySorted.get(yearlySorted.size() - 1);
        if (latest.getEps() == null) return null;

        Map<String, BigDecimal> equityRates = computeGrowthRates(yearlySorted, "equity");
        BigDecimal growth = equityRates.get("10yr");
        if (growth == null) growth = equityRates.get("5yr");
        if (growth == null) growth = new BigDecimal("10.00"); // conservative default, per the book's spirit

        BigDecimal futurePe = defaultFuturePe(growth);

        return new StickerSuggestion(latest.getEps(), growth, futurePe);
    }

    public record StickerSuggestion(
            BigDecimal currentEps,
            BigDecimal estimatedGrowthPct,
            BigDecimal estimatedFuturePe
    ) {}

    private BigDecimal round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    public record StickerPriceResult(
            BigDecimal futureEps10y,
            BigDecimal futurePrice,
            BigDecimal stickerPrice,
            BigDecimal marginOfSafetyPrice
    ) {}

    /**
     * Combines Big Five pass/fail (60% weight) and checklist completion (40% weight)
     * into a single 1-10 business quality score. Weights are intentionally simple and
     * transparent (not a black box) — see breakdown map returned for the UI to display.
     */
    public ScoreResult computeBusinessScore(
            Map<String, Boolean> bigFivePassFail,   // e.g. {"roic": true, "sales": true, ...}
            long checklistTotal,
            long checklistChecked,
            boolean priceBelowMarginOfSafety
    ) {
        long bigFivePassed = bigFivePassFail.values().stream().filter(Boolean::booleanValue).count();
        double bigFiveScore = bigFivePassFail.isEmpty() ? 0 : (bigFivePassed / (double) bigFivePassFail.size()) * 6.0; // out of 6

        double checklistScore = checklistTotal == 0 ? 0 : (checklistChecked / (double) checklistTotal) * 3.0; // out of 3

        double mosBonus = priceBelowMarginOfSafety ? 1.0 : 0.0; // out of 1

        double total = Math.max(1.0, Math.min(10.0, bigFiveScore + checklistScore + mosBonus));

        Map<String, Object> breakdown = Map.of(
                "bigFiveScoreOf6", round(bigFiveScore),
                "checklistScoreOf3", round(checklistScore),
                "marginOfSafetyBonusOf1", mosBonus,
                "bigFivePassed", bigFivePassed,
                "bigFiveTotal", bigFivePassFail.size()
        );

        return new ScoreResult(round(total), breakdown);
    }

    public record ScoreResult(BigDecimal score, Map<String, Object> breakdown) {}
}

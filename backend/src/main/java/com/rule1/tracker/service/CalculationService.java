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
     * Given a sorted (oldest -> newest) list of yearly Big Five rows, compute the
     * 10-yr / 5-yr / 1-yr growth rates for each metric, mirroring the book's
     * recommendation to look at ROIC over three windows.
     */
    public Map<String, BigDecimal> computeGrowthRates(List<BigFiveMetric> yearlySorted, String field) {
        int n = yearlySorted.size();
        if (n < 2) return Map.of();

        BigFiveMetric latest = yearlySorted.get(n - 1);
        Map<String, BigDecimal> result = new java.util.HashMap<>();

        result.put("10yr", growthFor(yearlySorted, field, Math.min(10, n - 1)));
        result.put("5yr", growthFor(yearlySorted, field, Math.min(5, n - 1)));
        result.put("1yr", growthFor(yearlySorted, field, 1));
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

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
     * not just a fixed 10/5/1.
     *
     * Critically, "N years" means an actual N-calendar-year gap between two data points, NOT
     * "N rows back in the list." If a user has only entered 2023 and 2026 (a 3-year gap, but
     * just 2 rows), the 1yr window must NOT silently compute the change between those two rows
     * and mislabel it "1yr" — it's actually a 3-year change. So each window looks for a row at
     * exactly (latest year − window) and returns null if no such row exists, rather than
     * falling back to whatever row happens to be adjacent in the list.
     */
    public Map<String, BigDecimal> computeGrowthRates(List<BigFiveMetric> yearlySorted, String field, int... windows) {
        Map<String, BigDecimal> result = new java.util.LinkedHashMap<>();
        for (int window : windows) {
            result.put(window + "yr", growthFor(yearlySorted, field, window));
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
     * The window is a calendar-year range (latest year − window + 1) through (latest year).
     * Critically, a window only gets a value if your data actually REACHES BACK that far — the
     * earliest year on file must be at or before the window's start year. Without this check, a
     * single data point (e.g. only 2026) would satisfy every window's date filter simultaneously
     * and show the same number under 10yr, 5yr, 3yr, and 1yr, which falsely implies years of
     * history that don't exist.
     */
    public Map<String, BigDecimal> averageRoic(List<BigFiveMetric> yearlySorted, int... windows) {
        Map<String, BigDecimal> result = new java.util.LinkedHashMap<>();
        if (yearlySorted.isEmpty()) {
            for (int window : windows) result.put(window + "yr", null);
            return result;
        }
        int latestYear = yearlySorted.get(yearlySorted.size() - 1).getFiscalYear();
        int earliestYear = yearlySorted.get(0).getFiscalYear();

        for (int window : windows) {
            int startYear = latestYear - window + 1;
            if (earliestYear > startYear) {
                result.put(window + "yr", null); // data doesn't actually reach back this far
                continue;
            }
            List<BigDecimal> values = yearlySorted.stream()
                    .filter(m -> m.getFiscalYear() >= startYear && m.getFiscalYear() <= latestYear)
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

    /**
     * Trend direction for each ROIC window: compares the earliest year's ROIC within that
     * window against the latest year's, so a user can see whether ROIC is improving, declining,
     * or flat — the average alone tells you the level, not the direction. Follows the exact
     * same "must actually reach back that far" rule as averageRoic, for the same reason.
     */
    public Map<String, String> roicTrend(List<BigFiveMetric> yearlySorted, int... windows) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        if (yearlySorted.isEmpty()) {
            for (int window : windows) result.put(window + "yr", null);
            return result;
        }
        int latestYear = yearlySorted.get(yearlySorted.size() - 1).getFiscalYear();
        int earliestYear = yearlySorted.get(0).getFiscalYear();
        BigDecimal latestVal = yearlySorted.get(yearlySorted.size() - 1).getRoicPct();

        for (int window : windows) {
            int startYear = latestYear - window + 1;
            if (earliestYear > startYear || latestVal == null) {
                result.put(window + "yr", null);
                continue;
            }
            BigDecimal firstVal = yearlySorted.stream()
                    .filter(m -> m.getFiscalYear() >= startYear && m.getFiscalYear() <= latestYear)
                    .map(BigFiveMetric::getRoicPct)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (firstVal == null) {
                result.put(window + "yr", null);
                continue;
            }
            int cmp = latestVal.compareTo(firstVal);
            result.put(window + "yr", cmp > 0 ? "UP" : cmp < 0 ? "DOWN" : "FLAT");
        }
        return result;
    }

    /**
     * Finds the row at exactly (latest fiscal year − yearsBack) and computes CAGR against the
     * latest row. Returns null if no row exists at that exact year — e.g. with only 2023 and
     * 2026 on file, yearsBack=1 looks for 2025 (not found → null), yearsBack=3 looks for 2023
     * (found → computed).
     */
    private BigDecimal growthFor(List<BigFiveMetric> sorted, String field, int yearsBack) {
        if (sorted.isEmpty() || yearsBack <= 0) return null;
        BigFiveMetric latest = sorted.get(sorted.size() - 1);
        int targetYear = latest.getFiscalYear() - yearsBack;

        BigFiveMetric startRow = sorted.stream()
                .filter(m -> m.getFiscalYear() == targetYear)
                .findFirst()
                .orElse(null);
        if (startRow == null) return null;

        BigDecimal startVal = extract(startRow, field);
        BigDecimal endVal = extract(latest, field);
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
     *  1. futureEPS = currentEPS * (1 + growthRate)^yearsToHold
     *  2. futurePrice = futureEPS * futurePE
     *  3. stickerPrice = futurePrice discounted back at the minimum acceptable rate of return
     *     over yearsToHold years
     *  4. marginOfSafetyPrice = 50% of stickerPrice
     * The book uses 10 years by default (per the 10-10 Rule), but the holding period is really
     * a user choice — the same number of years is used for both growing EPS forward and
     * discounting the future price back, since they represent the same holding horizon.
     */
    public StickerPriceResult calculateStickerPrice(
            BigDecimal currentEps,
            BigDecimal estimatedGrowthPct,   // e.g. 15 for 15%
            BigDecimal estimatedFuturePe,
            BigDecimal minAcceptableReturnPct, // e.g. 15 for 15%
            int yearsToHold
    ) {
        double eps = currentEps.doubleValue();
        double g = estimatedGrowthPct.doubleValue() / 100.0;
        double pe = estimatedFuturePe.doubleValue();
        double mar = minAcceptableReturnPct.doubleValue() / 100.0;
        int years = yearsToHold > 0 ? yearsToHold : 10;

        double futureEps = eps * Math.pow(1 + g, years);
        double futurePrice = futureEps * pe;
        double stickerPrice = futurePrice / Math.pow(1 + mar, years);
        double mosPrice = stickerPrice * 0.5;

        return new StickerPriceResult(
                round(futureEps), round(futurePrice), round(stickerPrice), round(mosPrice)
        );
    }

    /** Backward-compatible default: the classic 10-year holding period. */
    public StickerPriceResult calculateStickerPrice(
            BigDecimal currentEps, BigDecimal estimatedGrowthPct,
            BigDecimal estimatedFuturePe, BigDecimal minAcceptableReturnPct
    ) {
        return calculateStickerPrice(currentEps, estimatedGrowthPct, estimatedFuturePe, minAcceptableReturnPct, 10);
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

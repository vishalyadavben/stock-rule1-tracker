package com.rule1.tracker.service;

import com.rule1.tracker.dto.InvestmentDtos.*;
import com.rule1.tracker.entity.*;
import com.rule1.tracker.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InvestmentService {

    private final InvestmentLotRepository lotRepository;
    private final InvestmentExitRepository exitRepository;
    private final StockRepository stockRepository;

    public InvestmentService(InvestmentLotRepository lotRepository, InvestmentExitRepository exitRepository,
                              StockRepository stockRepository) {
        this.lotRepository = lotRepository;
        this.exitRepository = exitRepository;
        this.stockRepository = stockRepository;
    }

    /** Records a new buy as a fresh lot. isPaperMoney defaults to false (real money) if not
     *  specified — a lot is only deletable later if it was explicitly marked paper money here. */
    public InvestmentLot buy(Long userId, BuyRequest req) {
        Stock stock = stockRepository.findByTicker(req.ticker().toUpperCase())
                .orElseThrow(() -> new RuntimeException("Stock not found — add it first via /api/stocks/{ticker}"));

        InvestmentLot lot = new InvestmentLot();
        lot.setUserId(userId);
        lot.setStockId(stock.getId());
        lot.setQuantity(req.quantity());
        lot.setRemainingQuantity(req.quantity());
        lot.setBuyPrice(req.buyPrice());
        lot.setBuyDate(req.buyDate() != null ? req.buyDate() : LocalDateTime.now());
        lot.setStatus(InvestmentLot.LotStatus.OPEN);
        lot.setIsPaperMoney(req.isPaperMoney() != null && req.isPaperMoney());
        lot.setCreatedAt(LocalDateTime.now());
        return lotRepository.save(lot);
    }

    /**
     * Records a sell against an existing lot. Supports partial sells.
     * The lot itself is NEVER deleted — its status moves to PARTIAL or CLOSED,
     * and the InvestmentExit row is permanent history.
     */
    public InvestmentExit sell(Long userId, SellRequest req) {
        InvestmentLot lot = lotRepository.findById(req.lotId())
                .orElseThrow(() -> new RuntimeException("Lot not found"));
        if (!lot.getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized for this lot");
        }
        if (req.quantity().compareTo(lot.getRemainingQuantity()) > 0) {
            throw new RuntimeException("Cannot sell more than remaining quantity");
        }

        BigDecimal realizedGain = req.sellPrice().subtract(lot.getBuyPrice()).multiply(req.quantity());
        BigDecimal realizedGainPct = req.sellPrice().subtract(lot.getBuyPrice())
                .divide(lot.getBuyPrice(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        InvestmentExit exit = new InvestmentExit();
        exit.setLotId(lot.getId());
        exit.setQuantitySold(req.quantity());
        exit.setSellPrice(req.sellPrice());
        exit.setSellDate(req.sellDate() != null ? req.sellDate() : LocalDateTime.now());
        exit.setRealizedGain(realizedGain);
        exit.setRealizedGainPct(realizedGainPct);
        exit.setNotes(req.notes());
        exit.setCreatedAt(LocalDateTime.now());
        exitRepository.save(exit);

        BigDecimal remaining = lot.getRemainingQuantity().subtract(req.quantity());
        lot.setRemainingQuantity(remaining);
        lot.setStatus(remaining.compareTo(BigDecimal.ZERO) == 0
                ? InvestmentLot.LotStatus.CLOSED
                : InvestmentLot.LotStatus.PARTIAL);
        lotRepository.save(lot);

        return exit;
    }

    public List<InvestmentLot> getActiveHoldings(Long userId) {
        return lotRepository.findByUserIdAndStatusNot(userId, InvestmentLot.LotStatus.CLOSED);
    }

    public List<InvestmentLot> getAllLots(Long userId) {
        return lotRepository.findByUserId(userId);
    }

    public List<InvestmentExit> getExitHistory(Long userId) {
        List<Long> lotIds = lotRepository.findByUserId(userId).stream().map(InvestmentLot::getId).toList();
        return exitRepository.findByLotIdIn(lotIds);
    }

    /** Deletes a lot (and, via DB cascade, every exit recorded against it) — but only if it was
     *  marked paper money at buy time. Real-money lots are never deletable, by design; this is
     *  a safety rail, not a technicality to work around. */
    public void deleteLot(Long userId, Long lotId) {
        InvestmentLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new RuntimeException("Lot not found"));
        if (!lot.getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized for this lot");
        }
        if (lot.getIsPaperMoney() == null || !lot.getIsPaperMoney()) {
            throw new RuntimeException("Only paper-money positions can be deleted. This lot was recorded as real money.");
        }
        lotRepository.delete(lot);
    }
}

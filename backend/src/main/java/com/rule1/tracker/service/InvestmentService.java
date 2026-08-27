package com.rule1.tracker.service;

import com.rule1.tracker.dto.InvestmentDtos.*;
import com.rule1.tracker.entity.*;
import com.rule1.tracker.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public InvestmentService(InvestmentLotRepository lotRepository, InvestmentExitRepository exitRepository,
                              StockRepository stockRepository, UserRepository userRepository,
                              PasswordEncoder passwordEncoder) {
        this.lotRepository = lotRepository;
        this.exitRepository = exitRepository;
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
     *  marked paper money at buy time. Real-money lots are never deletable this way; use
     *  deleteLotWithPasswordConfirmation for those instead. */
    public void deleteLot(Long userId, Long lotId) {
        InvestmentLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new RuntimeException("Lot not found"));
        if (!lot.getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized for this lot");
        }
        if (lot.getIsPaperMoney() == null || !lot.getIsPaperMoney()) {
            throw new RuntimeException("Only paper-money positions can be deleted without password confirmation. Use the confirmed-delete option for real-money positions.");
        }
        lotRepository.delete(lot);
    }

    /** Edits a lot's buy price and/or buy date — the correction people actually need (a typo
     *  at entry time). Quantity is deliberately NOT editable here: it interacts with
     *  remainingQuantity and any recorded exits in ways that would be easy to corrupt, so a
     *  quantity correction should go through delete-and-re-add instead.
     *  The paper/real designation itself can never be changed after creation — allowing that
     *  would let someone mark a real position as "paper" purely to unlock deletion, which
     *  defeats the whole protection.
     *  password is required and verified when the lot is real money; ignored for paper money. */
    public InvestmentLot editLot(Long userId, Long lotId, BigDecimal buyPrice, LocalDateTime buyDate, String password) {
        InvestmentLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new RuntimeException("Lot not found"));
        if (!lot.getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized for this lot");
        }
        boolean isReal = lot.getIsPaperMoney() == null || !lot.getIsPaperMoney();
        if (isReal) {
            requireValidPassword(userId, password);
        }
        if (buyPrice != null) lot.setBuyPrice(buyPrice);
        if (buyDate != null) lot.setBuyDate(buyDate);
        return lotRepository.save(lot);
    }

    /** Deletes a real-money lot (and its exits) after verifying the user's password.
     *  Also usable for paper-money lots, in which case the password is still checked for
     *  consistency of the confirmation flow, but callers can use the simpler deleteLot()
     *  above for paper positions if they'd rather skip that step. */
    public void deleteLotWithPasswordConfirmation(Long userId, Long lotId, String password) {
        InvestmentLot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new RuntimeException("Lot not found"));
        if (!lot.getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized for this lot");
        }
        requireValidPassword(userId, password);
        lotRepository.delete(lot);
    }

    /** Edits a sell record's price, date, or notes and recalculates realized gain accordingly.
     *  Requires password confirmation when the underlying lot is real money. */
    public InvestmentExit editExit(Long userId, Long exitId, BigDecimal sellPrice, LocalDateTime sellDate,
                                    String notes, String password) {
        InvestmentExit exit = exitRepository.findById(exitId)
                .orElseThrow(() -> new RuntimeException("Exit not found"));
        InvestmentLot lot = lotRepository.findById(exit.getLotId())
                .orElseThrow(() -> new RuntimeException("Lot not found"));
        if (!lot.getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized for this exit");
        }
        boolean isReal = lot.getIsPaperMoney() == null || !lot.getIsPaperMoney();
        if (isReal) {
            requireValidPassword(userId, password);
        }
        if (sellPrice != null) exit.setSellPrice(sellPrice);
        if (sellDate != null) exit.setSellDate(sellDate);
        if (notes != null) exit.setNotes(notes);

        BigDecimal realizedGain = exit.getSellPrice().subtract(lot.getBuyPrice()).multiply(exit.getQuantitySold());
        BigDecimal realizedGainPct = exit.getSellPrice().subtract(lot.getBuyPrice())
                .divide(lot.getBuyPrice(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        exit.setRealizedGain(realizedGain);
        exit.setRealizedGainPct(realizedGainPct);

        return exitRepository.save(exit);
    }

    /** Deletes a single sell record and restores the sold quantity back onto the lot (rather
     *  than just leaving the lot's remaining quantity as if that sell had never been undone).
     *  Requires password confirmation when the underlying lot is real money. */
    public void deleteExit(Long userId, Long exitId, String password) {
        InvestmentExit exit = exitRepository.findById(exitId)
                .orElseThrow(() -> new RuntimeException("Exit not found"));
        InvestmentLot lot = lotRepository.findById(exit.getLotId())
                .orElseThrow(() -> new RuntimeException("Lot not found"));
        if (!lot.getUserId().equals(userId)) {
            throw new RuntimeException("Not authorized for this exit");
        }
        boolean isReal = lot.getIsPaperMoney() == null || !lot.getIsPaperMoney();
        if (isReal) {
            requireValidPassword(userId, password);
        }

        BigDecimal restored = lot.getRemainingQuantity().add(exit.getQuantitySold());
        lot.setRemainingQuantity(restored);
        lot.setStatus(restored.compareTo(lot.getQuantity()) >= 0
                ? InvestmentLot.LotStatus.OPEN
                : InvestmentLot.LotStatus.PARTIAL);
        lotRepository.save(lot);

        exitRepository.delete(exit);
    }

    private void requireValidPassword(Long userId, String password) {
        if (password == null || password.isBlank()) {
            throw new RuntimeException("Password confirmation is required to edit or delete a real-money record.");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Incorrect password.");
        }
    }
}

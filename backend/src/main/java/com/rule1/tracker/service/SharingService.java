package com.rule1.tracker.service;

import com.rule1.tracker.entity.Stock;
import com.rule1.tracker.entity.StockShare;
import com.rule1.tracker.entity.User;
import com.rule1.tracker.repository.StockRepository;
import com.rule1.tracker.repository.StockShareRepository;
import com.rule1.tracker.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Governs sharing of a user's checklist responses, Sticker Price calculations, and business
 * score for a specific stock with another user by email. Big Five data itself (API-fetched or
 * manual) is NOT part of this — it's already global per stock, visible to any user analyzing
 * that ticker, by original design.
 */
@Service
public class SharingService {

    private final StockShareRepository shareRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public SharingService(StockShareRepository shareRepository, StockRepository stockRepository,
                           UserRepository userRepository, EmailService emailService) {
        this.shareRepository = shareRepository;
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    /** Creates or updates a share grant. Idempotent by (owner, stock, email) — re-sharing with
     *  the same email just updates the permission level rather than creating a duplicate. */
    public StockShare shareStock(Long ownerUserId, Long stockId, String email, StockShare.Permission permission) {
        String normalizedEmail = email.trim().toLowerCase();
        StockShare share = shareRepository.findByStockIdAndOwnerUserIdAndSharedWithEmail(stockId, ownerUserId, normalizedEmail)
                .orElseGet(StockShare::new);

        boolean isNew = share.getId() == null;
        share.setStockId(stockId);
        share.setOwnerUserId(ownerUserId);
        share.setSharedWithEmail(normalizedEmail);
        share.setPermission(permission);
        if (share.getCreatedAt() == null) share.setCreatedAt(LocalDateTime.now());

        userRepository.findByEmail(normalizedEmail).ifPresent(u -> share.setSharedWithUserId(u.getId()));

        StockShare saved = shareRepository.save(share);

        if (isNew) {
            Stock stock = stockRepository.findById(stockId).orElse(null);
            User owner = userRepository.findById(ownerUserId).orElse(null);
            if (stock != null && owner != null) {
                emailService.sendShareInviteEmail(normalizedEmail, owner.getEmail(), stock.getTicker(), permission.name());
            }
        }
        return saved;
    }

    public List<StockShare> listMySharesForStock(Long ownerUserId, Long stockId) {
        return shareRepository.findByStockIdAndOwnerUserId(stockId, ownerUserId);
    }

    public List<StockShare> listSharedWithMe(Long userId) {
        return shareRepository.findBySharedWithUserId(userId);
    }

    public void revokeShare(Long ownerUserId, Long shareId) {
        StockShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new RuntimeException("Share not found"));
        if (!share.getOwnerUserId().equals(ownerUserId)) {
            throw new RuntimeException("Not authorized to revoke this share");
        }
        shareRepository.delete(share);
    }

    /**
     * Resolves which user_id's records an endpoint should actually operate on.
     * If requestedOwnerId is null or equals the requester's own id, returns the requester's own
     * id (normal, non-shared access). Otherwise requires a StockShare granting the requester
     * access to requestedOwnerId's data for this stock, at least at the required permission
     * level — throws if missing or insufficient (VIEW granted but EDIT required).
     */
    public Long resolveEffectiveOwner(Long requesterId, Long requestedOwnerId, Long stockId,
                                       StockShare.Permission required) {
        if (requestedOwnerId == null || requestedOwnerId.equals(requesterId)) {
            return requesterId;
        }
        StockShare share = shareRepository
                .findByStockIdAndOwnerUserIdAndSharedWithUserId(stockId, requestedOwnerId, requesterId)
                .orElseThrow(() -> new RuntimeException("This analysis hasn't been shared with you"));

        if (required == StockShare.Permission.EDIT && share.getPermission() != StockShare.Permission.EDIT) {
            throw new RuntimeException("You only have view access to this analysis — the owner hasn't granted edit permission");
        }
        return requestedOwnerId;
    }

    /** Called once at registration so an invite sent before someone had an account gets linked
     *  the moment they sign up with that email. */
    public void linkPendingSharesForNewUser(User newUser) {
        List<StockShare> pending = shareRepository.findBySharedWithEmailAndSharedWithUserIdIsNull(
                newUser.getEmail().trim().toLowerCase());
        for (StockShare share : pending) {
            share.setSharedWithUserId(newUser.getId());
            shareRepository.save(share);
        }
    }
}

package edu.sjsu.cmpe172.hpc.service;
import edu.sjsu.cmpe172.hpc.dto.*;
import edu.sjsu.cmpe172.hpc.exception.ApiException;
import edu.sjsu.cmpe172.hpc.repository.*;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
@Service
public class ProviderService {
    private final ProviderTransactionService transactions;
    private final SlotRepository slots;
    private final CatalogRepository catalog;
    private final SqliteRetry retry;
    private final Clock clock;
    public ProviderService(ProviderTransactionService transactions, SlotRepository slots,
                           CatalogRepository catalog, SqliteRetry retry, Clock clock) {
        this.transactions = transactions; this.slots = slots; this.catalog = catalog; this.retry = retry; this.clock = clock;
    }
    public SlotDto create(long userId, SlotRequest request) {
        try { return retry.execute(() -> transactions.create(userId, request)); }
        catch (DataIntegrityViolationException ex) { throw ApiException.conflict("This availability slot already exists"); }
    }
    public void remove(long userId, long slotId, long version) {
        retry.execute(() -> { transactions.remove(userId, slotId, version); return null; });
    }
    public PageDto<SlotDto> list(long userId, int page, int size) {
        return slots.browse(catalog.providerIdForUser(userId), null, null, null, false, true, page, size, LocalDateTime.now(clock));
    }
    public long providerId(long userId) { return catalog.providerIdForUser(userId); }
}

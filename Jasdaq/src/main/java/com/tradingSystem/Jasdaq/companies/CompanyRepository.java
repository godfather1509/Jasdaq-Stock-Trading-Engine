package com.tradingSystem.Jasdaq.companies;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CompanyRepository extends JpaRepository<Companies, String> {

    /** Atomically deducts qty from availableShares only if sufficient shares exist. Returns 1 on success, 0 if insufficient. */
    @Modifying
    @Query("UPDATE Companies c SET c.availableShares = c.availableShares - :qty WHERE c.companyId = :id AND c.availableShares >= :qty")
    int decrementAvailableShares(@Param("id") String companyId, @Param("qty") int qty);

    @Modifying
    @Query(value = "UPDATE Companies SET available_shares = LEAST(available_shares + :qty, total_shares) WHERE company_id = :id", nativeQuery = true)
    void incrementAvailableShares(@Param("id") String companyId, @Param("qty") int qty);

    /** Atomically updates only the current price — never touches availableShares. */
    @Modifying
    @Query("UPDATE Companies c SET c.currentPrice = :price WHERE c.companyId = :id")
    void updateCurrentPrice(@Param("id") String companyId, @Param("price") long price);

    /** Atomically raises allTimeHigh to :price if :price is greater than the current value. */
    @Modifying
    @Query(value = "UPDATE Companies SET all_time_high = GREATEST(COALESCE(all_time_high, 0), :price) WHERE company_id = :id", nativeQuery = true)
    void updateAllTimeHighIfNeeded(@Param("id") String companyId, @Param("price") long price);

    /** One-time idempotent repair: initialises allTimeHigh for any row where it was not set yet. */
    @Transactional
    @Modifying
    @Query(value = """
        UPDATE Companies c
        SET c.all_time_high = GREATEST(
            COALESCE(c.initial_price, 0),
            COALESCE(c.current_price, 0),
            COALESCE((SELECT MAX(t.price) FROM Trades t WHERE t.symbol = c.symbol), 0)
        )
        WHERE c.all_time_high IS NULL OR c.all_time_high = 0
        """, nativeQuery = true)
    void initAllTimeHighForExisting();
}

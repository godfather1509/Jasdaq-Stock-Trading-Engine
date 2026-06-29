package com.tradingSystem.Jasdaq.companies;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}

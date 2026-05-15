package com.ledgerlens.merchant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    @Query(value = """
            SELECT canonical_name
            FROM merchants
            WHERE similarity(canonical_name, :vendorName) > :threshold
            ORDER BY similarity(canonical_name, :vendorName) DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<String> findBestMatch(@Param("vendorName") String vendorName,
                                   @Param("threshold") double threshold);
}
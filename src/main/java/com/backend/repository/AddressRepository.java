package com.backend.repository;

import com.backend.model.AddressEntity;
import com.backend.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends JpaRepository<AddressEntity, Long> {
    AddressEntity findByUseridAndAddressType(Long userid, Integer addressType);
}

package com.cloudmen.backend.repositories;

import com.cloudmen.backend.domain.models.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    Optional<User> findByAuth0Id(String auth0Id);

    Optional<User> findByCustomerGoogleId(String customerGoogleId);

    List<User> findByPrimaryDomain(String primaryDomain);
}

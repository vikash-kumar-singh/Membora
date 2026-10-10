package com.membora.platform.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uq_user_email", columnNames = "email"))
public class UserAccount {
    @Id public UUID id;
    @Column(nullable = false) public String email;
    @Column(nullable = false) public String passwordHash;
    @Column(nullable = false) public String displayName;
    @Column(nullable = false) public String status;
    public Instant createdAt;
    protected UserAccount() {}
    public UserAccount(String email, String passwordHash, String displayName) {
        this.id = UUID.randomUUID(); this.email = email.toLowerCase().trim(); this.passwordHash = passwordHash;
        this.displayName = displayName; this.status = "ACTIVE"; this.createdAt = Instant.now();
    }
}

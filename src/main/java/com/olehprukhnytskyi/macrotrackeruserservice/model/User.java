package com.olehprukhnytskyi.macrotrackeruserservice.model;

import com.olehprukhnytskyi.util.AuthProvider;
import com.olehprukhnytskyi.util.UserRole;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 320, nullable = false, unique = true)
    private String email;

    @Column(length = 64)
    private String password;

    @ElementCollection(targetClass = AuthProvider.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "user_providers", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "provider", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<AuthProvider> authProviders = new HashSet<>();

    @Column(nullable = false)
    private boolean emailConfirmed = false;

    @Column
    private String confirmationCode;

    @Column
    private LocalDateTime confirmationCodeExpiresAt;

    @Column
    private String resetPasswordCode;

    @Column
    private LocalDateTime resetPasswordCodeExpiresAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserProfile profile;

    @ElementCollection(targetClass = UserRole.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<UserRole> roles = new HashSet<>();

    public void addAuthProvider(AuthProvider provider) {
        this.authProviders.add(provider);
    }

    public void addRole(UserRole role) {
        this.roles.add(role);
    }
}

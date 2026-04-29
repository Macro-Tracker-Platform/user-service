package com.olehprukhnytskyi.macrotrackeruserservice.model;

import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.BodyType;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "user_profiles")
public class UserProfile {
    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false)
    private Integer weight;

    @Column(nullable = false)
    private Integer goalWeight;

    @Column(nullable = false)
    private Integer height;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    private BodyType bodyType;

    @Enumerated(EnumType.STRING)
    private Goal goal;

    @Column(nullable = false)
    private Integer calories;

    @Column(nullable = false)
    private Integer carbohydrates;

    @Column(nullable = false)
    private Integer fat;

    @Column(nullable = false)
    private Integer protein;
}

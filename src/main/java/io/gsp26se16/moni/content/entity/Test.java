package io.gsp26se16.moni.content.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;

import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestMode;
import io.gsp26se16.moni.common.enumeration.TestType;
import io.gsp26se16.moni.tag.entity.Tag;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Test {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    String title;

    String description;

    @Enumerated(EnumType.STRING)
    Skill skill;

    @Enumerated(EnumType.STRING)
    TestType testType;

    @Column(name = "duration")
    Integer duration; // Thời gian làm bài (phút)

    @Enumerated(EnumType.STRING)
    @Column(name = "test_mode")
    TestMode testMode; // Enum: FULL_TEST, PRACTICE

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    PublishStatus status; // Enum: DRAFT, PUBLISHED, HIDDEN

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "test_tags", // Tên bảng trung gian
            joinColumns = @JoinColumn(name = "test_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    Set<Tag> tags = new HashSet<>();
}

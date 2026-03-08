package io.gsp26se16.moni.content.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
import io.gsp26se16.moni.common.enumeration.TestType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Stimulus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;

    String title;

    @Enumerated(EnumType.STRING)
    TestType testType;

    Skill skill;
    @Column(columnDefinition = "TEXT")
    String content;
    String mediaUrl;
    @Enumerated(EnumType.STRING)
    PublishStatus status;
    @Column(columnDefinition = "TEXT")
    String metadata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    Users createdBy;

    @OneToMany(mappedBy = "stimulus", cascade = CascadeType.ALL)
    List<QuestionGroup> questionGroups = new ArrayList<>();
}
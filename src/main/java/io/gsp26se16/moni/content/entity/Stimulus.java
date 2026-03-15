package io.gsp26se16.moni.content.entity;

import java.util.*;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.enumeration.PublishStatus;
import io.gsp26se16.moni.common.enumeration.Skill;
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vison_analysis_result", columnDefinition = "jsonb")
    Map<String, Object> visonAnalysisResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcript", columnDefinition = "jsonb")
    List<Map<String, Object>> transcript;

    @OneToMany(mappedBy = "stimulus", cascade = CascadeType.ALL)
    List<QuestionGroup> questionGroups = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "stimulus_tag",
            joinColumns = @JoinColumn(name = "stimulus_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();
}

package io.gsp26se16.moni.tag.repository;

import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.entity.TagType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findByType(TagType type);

    // 2. Tìm kiếm theo tên (Có chứa từ khóa, không phân biệt hoa thường)
    List<Tag> findByNameContainingIgnoreCase(String keyword);

    // 3. Kiểm tra trùng mã (Dùng khi tạo mới)
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}

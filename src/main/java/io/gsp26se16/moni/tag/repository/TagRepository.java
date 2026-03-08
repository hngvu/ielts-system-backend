package io.gsp26se16.moni.tag.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.gsp26se16.moni.tag.entity.Tag;
import io.gsp26se16.moni.tag.entity.TagType;

@Repository
public interface TagRepository extends JpaRepository<Tag, Integer> {
    List<Tag> findByType(TagType type);

    // 2. Tìm kiếm theo tên (Có chứa từ khóa, không phân biệt hoa thường)
    List<Tag> findByNameContainingIgnoreCase(String keyword);

    // 3. Kiểm tra trùng mã (Dùng khi tạo mới)
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Integer id);
}

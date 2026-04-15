-- Dọn dẹp các tags cũ nếu muốn (Tuỳ chọn: Cẩn thận khi chạy trên Production)
-- Xóa reference ở các bảng nối trước
-- DELETE FROM curated_word_tag;
-- DELETE FROM question_tag;
-- DELETE FROM tags WHERE code LIKE 'CEFR_%' OR code LIKE 'TOPIC_%' OR code LIKE 'DIF_%';

-- 1. Thêm Tag Độ Khó IELTS (DIF_)
INSERT INTO tags (name, code, tag_type, description, created_at, updated_at) 
VALUES 
('Easy (Band 0 - 4.5)', 'DIF_EASY', 'DIFFICULTY', 'Dành cho người mới bắt đầu', NOW(), NOW()),
('Medium (Band 5.0 - 6.0)', 'DIF_MEDIUM', 'DIFFICULTY', 'Mức độ trung bình khá', NOW(), NOW()),
('Hard (Band 6.5+)', 'DIF_HARD', 'DIFFICULTY', 'Mức độ nâng cao', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- 2. Thêm Tag Độ Khó CEFR cho Vocabulary (CEFR_)
INSERT INTO tags (name, code, tag_type, description, created_at, updated_at) 
VALUES 
('A1', 'CEFR_A1', 'DIFFICULTY', 'Beginner', NOW(), NOW()),
('A2', 'CEFR_A2', 'DIFFICULTY', 'Elementary', NOW(), NOW()),
('B1', 'CEFR_B1', 'DIFFICULTY', 'Intermediate', NOW(), NOW()),
('B2', 'CEFR_B2', 'DIFFICULTY', 'Upper Intermediate', NOW(), NOW()),
('C1', 'CEFR_C1', 'DIFFICULTY', 'Advanced', NOW(), NOW()),
('C2', 'CEFR_C2', 'DIFFICULTY', 'Proficient', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- 3. Thêm Tag Chủ Đề (TOPIC_)
INSERT INTO tags (name, code, tag_type, description, created_at, updated_at) 
VALUES 
('Education', 'TOPIC_EDU', 'TOPIC', 'Giáo dục, trường học', NOW(), NOW()),
('Environment', 'TOPIC_ENV', 'TOPIC', 'Môi trường, khí hậu', NOW(), NOW()),
('Technology', 'TOPIC_TECH', 'TOPIC', 'Công nghệ, AI', NOW(), NOW()),
('Health', 'TOPIC_HEALTH', 'TOPIC', 'Sức khỏe, Y tế', NOW(), NOW()),
('Science', 'TOPIC_SCI', 'TOPIC', 'Khoa học', NOW(), NOW()),
('Business', 'TOPIC_BIZ', 'TOPIC', 'Thương mại, kinh doanh', NOW(), NOW()),
('Travel', 'TOPIC_TRAVEL', 'TOPIC', 'Du lịch, văn hóa', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- ==========================================================
-- 1. DỌN DẸP DỮ LIỆU CŨ (CLEANUP)
-- Xóa reference ở các bảng nối trước để tránh lỗi FK
-- ==========================================================
DELETE FROM curated_word_tag;
DELETE FROM question_tag;
DELETE FROM stimulus_tag;
DELETE FROM learner_metric;
DELETE FROM tags;

-- ==========================================================
-- 2. ĐỘ KHÓ IELTS (DIF_)
-- ==========================================================
INSERT INTO tags (name, code, tag_type, description, created_at, updated_at) 
VALUES 
('Easy (Band 0 - 4.5)', 'DIF_EASY', 'DIFFICULTY', 'Dành cho người mới bắt đầu', NOW(), NOW()),
('Medium (Band 5.0 - 6.0)', 'DIF_MEDIUM', 'DIFFICULTY', 'Mức độ trung bình khá', NOW(), NOW()),
('Hard (Band 6.5+)', 'DIF_HARD', 'DIFFICULTY', 'Mức độ nâng cao', NOW(), NOW());

-- ==========================================================
-- 3. ĐỘ KHÓ CEFR CHO VOCABULARY (CEFR_)
-- ==========================================================
INSERT INTO tags (name, code, tag_type, description, created_at, updated_at) 
VALUES 
('A1', 'CEFR_A1', 'DIFFICULTY', 'Beginner', NOW(), NOW()),
('A2', 'CEFR_A2', 'DIFFICULTY', 'Elementary', NOW(), NOW()),
('B1', 'CEFR_B1', 'DIFFICULTY', 'Intermediate', NOW(), NOW()),
('B2', 'CEFR_B2', 'DIFFICULTY', 'Upper Intermediate', NOW(), NOW()),
('C1', 'CEFR_C1', 'DIFFICULTY', 'Advanced', NOW(), NOW()),
('C2', 'CEFR_C2', 'DIFFICULTY', 'Proficient', NOW(), NOW());

-- ==========================================================
-- 4. LOẠI CÂU HỎI (QUESTION_TYPE)
-- ==========================================================
INSERT INTO tags (name, code, tag_type, description, created_at, updated_at) 
VALUES 
('Multiple Choice', 'QT_MCQ', 'QUESTION_TYPE', 'Trắc nghiệm nhiều lựa chọn', NOW(), NOW()),
('Matching Headings', 'QT_MATCH_HEADING', 'QUESTION_TYPE', 'Nối tiêu đề đoạn văn', NOW(), NOW()),
('Matching Information', 'QT_MATCH_INFO', 'QUESTION_TYPE', 'Nối thông tin vào đoạn văn', NOW(), NOW()),
('Matching Features', 'QT_MATCH_FEAT', 'QUESTION_TYPE', 'Nối đặc điểm/nhân vật', NOW(), NOW()),
('Matching Sentence Endings', 'QT_MATCH_END', 'QUESTION_TYPE', 'Nối phần kết của câu', NOW(), NOW()),
('True/False/Not Given', 'QT_TFNG', 'QUESTION_TYPE', 'Xác định thông tin đúng/sai/không đề cập', NOW(), NOW()),
('Yes/No/Not Given', 'QT_YNNG', 'QUESTION_TYPE', 'Xác định quan điểm tác giả', NOW(), NOW()),
('Sentence Completion', 'QT_SENT_COMP', 'QUESTION_TYPE', 'Điền vào chỗ trống trong câu', NOW(), NOW()),
('Summary Completion', 'QT_SUM_COMP', 'QUESTION_TYPE', 'Hoàn thành bản tóm tắt', NOW(), NOW()),
('Note/Table/Flow-chart Completion', 'QT_TABLE_COMP', 'QUESTION_TYPE', 'Hoàn thành bảng/sơ đồ/ghi chú', NOW(), NOW()),
('Diagram/Map Labeling', 'QT_DIAG_LABEL', 'QUESTION_TYPE', 'Dán nhãn sơ đồ/bản đồ', NOW(), NOW()),
('Short-answer Questions', 'QT_SHORT_ANS', 'QUESTION_TYPE', 'Trả lời câu hỏi ngắn', NOW(), NOW()),
('Task 1: Line Graph', 'QT_W1_LINE', 'QUESTION_TYPE', 'Biểu đồ đường', NOW(), NOW()),
('Task 1: Bar Chart', 'QT_W1_BAR', 'QUESTION_TYPE', 'Biểu đồ cột', NOW(), NOW()),
('Task 1: Pie Chart', 'QT_W1_PIE', 'QUESTION_TYPE', 'Biểu đồ tròn', NOW(), NOW()),
('Task 1: Table', 'QT_W1_TABLE', 'QUESTION_TYPE', 'Bảng số liệu', NOW(), NOW()),
('Task 1: Mixed Chart', 'QT_W1_MIXED', 'QUESTION_TYPE', 'Biểu đồ kết hợp', NOW(), NOW()),
('Task 1: Process', 'QT_W1_PROC', 'QUESTION_TYPE', 'Quy trình/Chu trình', NOW(), NOW()),
('Task 1: Map', 'QT_W1_MAP', 'QUESTION_TYPE', 'Bản đồ so sánh', NOW(), NOW()),
('Task 2: Essay', 'QT_W2_ESSAY', 'QUESTION_TYPE', 'Bài luận xã hội', NOW(), NOW());

-- ==========================================================
-- 5. KỸ NĂNG (SKILL)
-- ==========================================================
INSERT INTO tags (name, code, tag_type, description, created_at, updated_at) 
VALUES 
('Listening', 'SKILL_LISTENING', 'SKILL', 'Kỹ năng Nghe IELTS', NOW(), NOW()),
('Reading', 'SKILL_READING', 'SKILL', 'Kỹ năng Đọc IELTS', NOW(), NOW()),
('Writing', 'SKILL_WRITING', 'SKILL', 'Kỹ năng Viết IELTS', NOW(), NOW()),
('Speaking', 'SKILL_SPEAKING', 'SKILL', 'Kỹ năng Nói IELTS', NOW(), NOW());

-- ==========================================================
-- 6. CHỦ ĐỀ (TOPIC)
-- ==========================================================
INSERT INTO tags (name, code, tag_type, description, created_at, updated_at) 
VALUES 
('Education', 'TOPIC_EDU', 'TOPIC', 'Giáo dục, trường học', NOW(), NOW()),
('Environment', 'TOPIC_ENV', 'TOPIC', 'Môi trường, khí hậu', NOW(), NOW()),
('Technology', 'TOPIC_TECH', 'TOPIC', 'Công nghệ, AI', NOW(), NOW()),
('Health', 'TOPIC_HEALTH', 'TOPIC', 'Sức khỏe, Y tế', NOW(), NOW()),
('Science', 'TOPIC_SCI', 'TOPIC', 'Khoa học', NOW(), NOW()),
('Business', 'TOPIC_BIZ', 'TOPIC', 'Thương mại, kinh doanh', NOW(), NOW()),
('Travel', 'TOPIC_TRAVEL', 'TOPIC', 'Du lịch, văn hóa', NOW(), NOW()),
('Arts & Culture', 'TOPIC_ARTS', 'TOPIC', 'Nghệ thuật và văn hóa', NOW(), NOW()),
('Society & Social Issues', 'TOPIC_SOCIETY', 'TOPIC', 'Xã hội và cộng đồng', NOW(), NOW()),
('Family & Children', 'TOPIC_FAM', 'TOPIC', 'Gia đình và trẻ em', NOW(), NOW()),
('Sport & Leisure', 'TOPIC_SPORT', 'TOPIC', 'Thể thao và giải trí', NOW(), NOW()),
('Crime & Law', 'TOPIC_CRIME', 'TOPIC', 'Tội phạm và luật pháp', NOW(), NOW()),
('Media & Advertising', 'TOPIC_MEDIA', 'TOPIC', 'Truyền thông và quảng cáo', NOW(), NOW()),
('Space & Astronomy', 'TOPIC_SPACE', 'TOPIC', 'Vũ trụ và thiên văn học', NOW(), NOW()),
('Work & Career', 'TOPIC_WORK', 'TOPIC', 'Công việc và nghề nghiệp', NOW(), NOW()),
('Animals & Wildlife', 'TOPIC_ANIMAL', 'TOPIC', 'Động vật và thế giới hoang dã', NOW(), NOW()),
('Food & Nutrition', 'TOPIC_FOOD', 'TOPIC', 'Thực phẩm và dinh dưỡng', NOW(), NOW()),
('History', 'TOPIC_HIST', 'TOPIC', 'Lịch sử', NOW(), NOW()),
('Economy', 'TOPIC_ECON', 'TOPIC', 'Kinh tế', NOW(), NOW()),
('Psychology', 'TOPIC_PSYCH', 'TOPIC', 'Tâm lý học', NOW(), NOW());

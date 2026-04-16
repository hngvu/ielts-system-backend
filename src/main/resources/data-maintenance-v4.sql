-- ==========================================================
-- 1. SEED STRUCTURAL TAGS (Khung Bài thi)
-- ==========================================================

-- READING PASSAGE
INSERT INTO tags (name, code, tag_type, description, created_at, updated_at) VALUES 
('Passage 1', 'READ_PASSAGE_1', 'PASSAGE', 'Reading Passage 1 (Dễ)', NOW(), NOW()),
('Passage 2', 'READ_PASSAGE_2', 'PASSAGE', 'Reading Passage 2 (Trung bình)', NOW(), NOW()),
('Passage 3', 'READ_PASSAGE_3', 'PASSAGE', 'Reading Passage 3 (Khó)', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- LISTENING SECTION
INSERT INTO tags (name, code, tag_type, description, created_at, updated_at) VALUES 
('Section 1 / Part 1', 'LIST_SECTION_1', 'SECTION', 'Listening Section 1', NOW(), NOW()),
('Section 2 / Part 2', 'LIST_SECTION_2', 'SECTION', 'Listening Section 2', NOW(), NOW()),
('Section 3 / Part 3', 'LIST_SECTION_3', 'SECTION', 'Listening Section 3', NOW(), NOW()),
('Section 4 / Part 4', 'LIST_SECTION_4', 'SECTION', 'Listening Section 4', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- WRITING TASK
INSERT INTO tags (name, code, tag_type, description, created_at, updated_at) VALUES 
('Task 1', 'WRIT_TASK_1', 'TASK', 'Writing Task 1', NOW(), NOW()),
('Task 2', 'WRIT_TASK_2', 'TASK', 'Writing Task 2', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- SPEAKING PART
INSERT INTO tags (name, code, tag_type, description, created_at, updated_at) VALUES 
('Part 1', 'SPEAK_PART_1', 'PART', 'Speaking Part 1: Introduction', NOW(), NOW()),
('Part 2', 'SPEAK_PART_2', 'PART', 'Speaking Part 2: Long Turn', NOW(), NOW()),
('Part 3', 'SPEAK_PART_3', 'PART', 'Speaking Part 3: Discussion', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- ==========================================================
-- 2. DYNAMICALLY MIGRATE EXISTING STIMULI 
-- Tự động mò Title của Stimulus (VD: "Passage 1", "Section 2") để gắn Tag tương ứng
-- Hệ thống tự lấy id của Tag vừa tạo móc nối sang id của Stimulus
-- ==========================================================

-- READING MIGRATION
INSERT INTO stimulus_tag (stimulus_id, tag_id)
SELECT s.id, t.id
FROM stimulus s
CROSS JOIN tags t
WHERE s.skill = 1 AND t.tag_type = 'PASSAGE'
  AND (
       (s.title ILIKE '%Passage 1%' AND t.code = 'READ_PASSAGE_1') OR
       (s.title ILIKE '%Passage 2%' AND t.code = 'READ_PASSAGE_2') OR
       (s.title ILIKE '%Passage 3%' AND t.code = 'READ_PASSAGE_3')
  )
ON CONFLICT (stimulus_id, tag_id) DO NOTHING;

-- LISTENING MIGRATION
INSERT INTO stimulus_tag (stimulus_id, tag_id)
SELECT s.id, t.id
FROM stimulus s
CROSS JOIN tags t
WHERE s.skill = 0 AND t.tag_type = 'SECTION'
  AND (
       (s.title ILIKE '%Section 1%' OR s.title ILIKE '%Part 1%' AND t.code = 'LIST_SECTION_1') OR
       (s.title ILIKE '%Section 2%' OR s.title ILIKE '%Part 2%' AND t.code = 'LIST_SECTION_2') OR
       (s.title ILIKE '%Section 3%' OR s.title ILIKE '%Part 3%' AND t.code = 'LIST_SECTION_3') OR
       (s.title ILIKE '%Section 4%' OR s.title ILIKE '%Part 4%' AND t.code = 'LIST_SECTION_4')
  )
ON CONFLICT (stimulus_id, tag_id) DO NOTHING;

-- WRITING MIGRATION
INSERT INTO stimulus_tag (stimulus_id, tag_id)
SELECT s.id, t.id
FROM stimulus s
CROSS JOIN tags t
WHERE s.skill = 3 AND t.tag_type = 'TASK'
  AND (
       (s.title ILIKE '%Task 1%' AND t.code = 'WRIT_TASK_1') OR
       (s.title ILIKE '%Task 2%' AND t.code = 'WRIT_TASK_2')
  )
ON CONFLICT (stimulus_id, tag_id) DO NOTHING;

-- SPEAKING MIGRATION
INSERT INTO stimulus_tag (stimulus_id, tag_id)
SELECT s.id, t.id
FROM stimulus s
CROSS JOIN tags t
WHERE s.skill = 2 AND t.tag_type = 'PART'
  AND (
       (s.title ILIKE '%Part 1%' AND t.code = 'SPEAK_PART_1') OR
       (s.title ILIKE '%Part 2%' AND t.code = 'SPEAK_PART_2') OR
       (s.title ILIKE '%Part 3%' AND t.code = 'SPEAK_PART_3')
  )
ON CONFLICT (stimulus_id, tag_id) DO NOTHING;

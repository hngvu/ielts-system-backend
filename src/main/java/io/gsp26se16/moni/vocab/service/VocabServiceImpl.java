package io.gsp26se16.moni.vocab.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.vocab.dto.ManualSaveVocabRequest;
import io.gsp26se16.moni.vocab.dto.SaveVocabRequest;
import io.gsp26se16.moni.vocab.dto.VocabDetailResponse;
import io.gsp26se16.moni.vocab.dto.VocabResponse;
import io.gsp26se16.moni.vocab.entity.Dictionary;
import io.gsp26se16.moni.vocab.entity.Vocab;
import io.gsp26se16.moni.vocab.entity.VocabList;
import io.gsp26se16.moni.vocab.enumeration.VocabSourceType;
import io.gsp26se16.moni.vocab.enumeration.VocabStatus;
import io.gsp26se16.moni.vocab.repository.DictionaryRepository;
import io.gsp26se16.moni.vocab.repository.VocabListRepository;
import io.gsp26se16.moni.vocab.repository.VocabRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VocabServiceImpl implements VocabService {

    private final VocabRepository vocabRepository;
    private final VocabListRepository vocabListRepository;
    private final DictionaryRepository dictionaryRepository;
    private final VocabLookupService vocabLookupService;
    private final VocabListServiceImpl vocabListServiceImpl;
    private final VocabAuthHelper authHelper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<VocabResponse> getMyWords(
            String credentialId, int page, int size, Integer listId, String search, String statusParam) {
        Users user = authHelper.getUser(credentialId);
        String userId = user.getId();
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        boolean hasSearch = search != null && !search.isBlank();

        Page<Vocab> result;
        if (statusParam != null && !statusParam.isBlank()) {
            boolean isManual = statusParam.equalsIgnoreCase("manual");
            if (isManual) {
                if (hasSearch) {
                    result = vocabRepository.findByUserIdAndSourceTypeAndWordContaining(
                            userId, VocabSourceType.MANUAL, search, pageable);
                } else {
                    result = vocabRepository.findByUserIdAndSourceType(userId, VocabSourceType.MANUAL, pageable);
                }
            } else {
                VocabStatus vocabStatus;
                try {
                    vocabStatus = VocabStatus.valueOf(statusParam.toUpperCase());
                } catch (IllegalArgumentException e) {
                    vocabStatus = VocabStatus.ACTIVE;
                }

                if (hasSearch) {
                    result = vocabRepository.findByUserIdAndStatusAndWordContaining(
                            userId, vocabStatus, search, pageable);
                } else {
                    result = vocabRepository.findByUserIdAndStatus(userId, vocabStatus, pageable);
                }
            }
        } else if (listId != null && hasSearch) {
            result = vocabRepository.findByUserIdAndVocabListIdAndStatusNotAndWordContaining(
                    userId, listId, VocabStatus.ARCHIVED, search, pageable);
        } else if (listId != null) {
            result = vocabRepository.findByUserIdAndVocabListIdAndStatusNot(
                    userId, listId, VocabStatus.ARCHIVED, pageable);
        } else if (hasSearch) {
            result = vocabRepository.findByUserIdAndStatusNotAndWordContaining(
                    userId, VocabStatus.ARCHIVED, search, pageable);
        } else {
            result = vocabRepository.findByUserIdAndStatusNot(userId, VocabStatus.ARCHIVED, pageable);
        }
        return result.map(this::toResponse);
    }

    @Override
    @Transactional
    public VocabResponse saveWord(String credentialId, SaveVocabRequest request) {
        Users user = authHelper.getUser(credentialId);
        String userId = user.getId();
        String word = request.getWord().trim().toLowerCase();

        // Return existing if already saved
        var existing = vocabRepository.findByUserIdAndWord(userId, word);
        if (existing.isPresent()) {
            Vocab v = existing.get();
            // Restore if archived
            if (v.getStatus() == VocabStatus.ARCHIVED) {
                v.setStatus(VocabStatus.ACTIVE);
                vocabRepository.save(v);
            }
            return toResponse(v);
        }

        // Find or create Dictionary entry
        Dictionary dict = findOrCreateDictionary(word, request.getSentence());

        // Resolve VocabList
        VocabList vocabList = resolveVocabList(user, request.getVocabListId());

        Vocab vocab = Vocab.builder()
                .word(word)
                .phonetic(dict.getPhonetic())
                .pos(dict.getPos())
                .definition(dict.getDefinition())
                .example(dict.getExample())
                .audioUrl(dict.getAudioUrl())
                .meaning(dict.getMeaning())
                .status(VocabStatus.ACTIVE)
                .sourceType(
                        request.getSourceType() != null ? request.getSourceType() : VocabSourceType.DICTIONARY_LOOKUP)
                .user(user)
                .vocabList(vocabList)
                .dictionary(dict)
                .build();

        return toResponse(vocabRepository.save(vocab));
    }

    @Override
    @Transactional
    public VocabResponse saveWordManual(String credentialId, ManualSaveVocabRequest request) {
        Users user = authHelper.getUser(credentialId);
        String userId = user.getId();
        String word = request.getWord().trim().toLowerCase();

        // Return existing if already saved
        var existing = vocabRepository.findByUserIdAndWord(userId, word);
        if (existing.isPresent()) {
            Vocab v = existing.get();
            // Restore if archived
            if (v.getStatus() == VocabStatus.ARCHIVED) {
                v.setStatus(VocabStatus.ACTIVE);
                vocabRepository.save(v);
            }
            return toResponse(v);
        }

        // Check if Dictionary entry already exists
        Dictionary dict = dictionaryRepository.findFirstByWordIgnoreCase(word).orElse(null);

        if (dict == null) {
            // Create new Dictionary entry
            dict = new Dictionary();
            dict.setWord(word);
            dict.setMeaning(request.getMeaning());
            dict.setPhonetic(request.getPhonetic());
            dict.setPos(request.getPos());
            dict.setDefinition(request.getDefinition());
            dict.setExample(request.getExample());
            dictionaryRepository.save(dict);
        } else {
            // Update existing Dictionary entry with new data if provided
            if (request.getMeaning() != null && !request.getMeaning().isBlank()) {
                dict.setMeaning(request.getMeaning());
            }
            if (request.getPhonetic() != null && !request.getPhonetic().isBlank()) {
                dict.setPhonetic(request.getPhonetic());
            }
            if (request.getPos() != null && !request.getPos().isBlank()) {
                dict.setPos(request.getPos());
            }
            if (request.getDefinition() != null && !request.getDefinition().isBlank()) {
                dict.setDefinition(request.getDefinition());
            }
            if (request.getExample() != null && !request.getExample().isBlank()) {
                dict.setExample(request.getExample());
            }
            dictionaryRepository.save(dict);
        }

        // Resolve VocabList
        VocabList vocabList = resolveVocabList(user, request.getVocabListId());

        Vocab vocab = Vocab.builder()
                .word(word)
                .phonetic(dict.getPhonetic())
                .pos(dict.getPos())
                .definition(dict.getDefinition())
                .example(dict.getExample())
                .audioUrl(dict.getAudioUrl())
                .meaning(dict.getMeaning())
                .status(VocabStatus.ACTIVE)
                .sourceType(request.getSourceType() != null ? request.getSourceType() : VocabSourceType.MANUAL)
                .user(user)
                .vocabList(vocabList)
                .dictionary(dict)
                .build();

        return toResponse(vocabRepository.save(vocab));
    }

    @Override
    @Transactional
    public void deleteWord(String credentialId, Integer vocabId) {
        Users user = authHelper.getUser(credentialId);
        Vocab vocab = vocabRepository
                .findById(vocabId)
                .filter(v -> v.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new AppException(ErrorCode.VOCAB_COLLECTION_NOT_FOUND));
        vocab.setStatus(VocabStatus.ARCHIVED);
        vocabRepository.save(vocab);
    }

    @Override
    @Transactional
    public void moveWord(String credentialId, Integer vocabId, Integer targetListId) {
        Users user = authHelper.getUser(credentialId);
        Vocab vocab = vocabRepository
                .findById(vocabId)
                .filter(v -> v.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new AppException(ErrorCode.VOCAB_COLLECTION_NOT_FOUND));
        VocabList targetList = vocabListRepository
                .findByIdAndUserId(targetListId, user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.VOCAB_COLLECTION_NOT_FOUND));
        vocab.setVocabList(targetList);
        vocabRepository.save(vocab);
    }

    @Override
    @Transactional(readOnly = true)
    public VocabDetailResponse getVocabDetail(String credentialId, Integer vocabId) {
        Users user = authHelper.getUser(credentialId);
        Vocab vocab = vocabRepository
                .findById(vocabId)
                .filter(v -> v.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new AppException(ErrorCode.VOCAB_NOT_FOUND));

        Dictionary dict = vocab.getDictionary();
        List<String> exampleList = new ArrayList<>();
        if (dict != null && dict.getExamples() != null && !dict.getExamples().isBlank()) {
            try {
                exampleList = objectMapper.readValue(dict.getExamples(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse examples for '{}': {}", vocab.getWord(), e.getMessage());
            }
        }

        return VocabDetailResponse.builder()
                .id(vocab.getId())
                .word(vocab.getWord())
                .phonetic(vocab.getPhonetic())
                .pos(vocab.getPos())
                .definition(vocab.getDefinition())
                .example(vocab.getExample())
                .audioUrl(vocab.getAudioUrl())
                .meaning(vocab.getMeaning())
                .status(vocab.getStatus().name())
                .collectionName(
                        vocab.getVocabList() != null ? vocab.getVocabList().getTitle() : null)
                .collocation(dict != null ? dict.getCollocation() : null)
                .explanation(dict != null ? dict.getExplanation() : null)
                .examples(exampleList.isEmpty() ? null : exampleList)
                .build();
    }

    // --- Helpers ---

    private Dictionary findOrCreateDictionary(String word, String sentence) {
        return dictionaryRepository.findFirstByWordIgnoreCase(word).orElseGet(() -> {
            try {
                var lookup = vocabLookupService.lookupWord(word);
                // lookupWord already saves to DB via upsert; refetch
                return dictionaryRepository.findFirstByWordIgnoreCase(word).orElseGet(() -> {
                    // Fallback: use upsert to avoid duplicate key on race condition
                    dictionaryRepository.upsert(
                            word,
                            lookup.getPhonetic(),
                            lookup.getPos(),
                            lookup.getMeaning(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
                    return dictionaryRepository.findFirstByWordIgnoreCase(word).orElseGet(() -> {
                        Dictionary d = new Dictionary();
                        d.setWord(word);
                        d.setMeaning(lookup.getMeaning());
                        return d;
                    });
                });
            } catch (Exception e) {
                log.warn("Dictionary lookup failed for '{}': {}", word, e.getMessage());
                // Use upsert to avoid duplicate key violation
                dictionaryRepository.upsert(word, null, null, null, null, null, null, null, null, null);
                return dictionaryRepository.findFirstByWordIgnoreCase(word).orElseGet(() -> {
                    Dictionary d = new Dictionary();
                    d.setWord(word);
                    return d;
                });
            }
        });
    }

    private VocabList resolveVocabList(Users user, Integer listId) {
        if (listId != null) {
            return vocabListRepository
                    .findByIdAndUserId(listId, user.getId())
                    .orElseGet(() -> vocabListServiceImpl.ensureDefaultList(user));
        }
        return vocabListServiceImpl.ensureDefaultList(user);
    }

    private VocabResponse toResponse(Vocab v) {
        // Parse examples from dictionary
        List<String> exampleList = new ArrayList<>();
        if (v.getDictionary() != null
                && v.getDictionary().getExamples() != null
                && !v.getDictionary().getExamples().isBlank()) {
            try {
                exampleList =
                        objectMapper.readValue(v.getDictionary().getExamples(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
            }
        }

        return VocabResponse.builder()
                .id(v.getId())
                .word(v.getWord())
                .phonetic(v.getPhonetic())
                .pos(v.getPos())
                .definition(v.getDefinition())
                .example(v.getExample())
                .meaning(v.getMeaning())
                .audioUrl(v.getAudioUrl())
                .status(v.getStatus())
                .sourceType(v.getSourceType())
                .collocation(v.getDictionary() != null ? v.getDictionary().getCollocation() : null)
                .explanation(v.getDictionary() != null ? v.getDictionary().getExplanation() : null)
                .examples(exampleList.isEmpty() ? null : exampleList)
                .collectionName(v.getVocabList() != null ? v.getVocabList().getTitle() : null)
                .nextReviewAt(v.getNextReviewAt())
                .createdAt(v.getCreatedAt())
                .build();
    }
}

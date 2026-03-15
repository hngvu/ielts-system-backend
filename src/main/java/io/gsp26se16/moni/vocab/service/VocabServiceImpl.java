package io.gsp26se16.moni.vocab.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.vocab.dto.SaveVocabRequest;
import io.gsp26se16.moni.vocab.dto.VocabResponse;
import io.gsp26se16.moni.vocab.entity.Dictionary;
import io.gsp26se16.moni.vocab.entity.Vocab;
import io.gsp26se16.moni.vocab.entity.VocabList;
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

    @Override
    public Page<VocabResponse> getMyWords(String credentialId, int page, int size, Integer listId, String search) {
        Users user = authHelper.getUser(credentialId);
        String userId = user.getId();
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        boolean hasSearch = search != null && !search.isBlank();

        Page<Vocab> result;
        if (listId != null && hasSearch) {
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

    // --- Helpers ---

    private Dictionary findOrCreateDictionary(String word, String sentence) {
        return dictionaryRepository.findFirstByWordIgnoreCase(word).orElseGet(() -> {
            try {
                var lookup = vocabLookupService.lookupWord(word, sentence);
                // lookupWord already saves to DB; refetch
                return dictionaryRepository.findFirstByWordIgnoreCase(word).orElseGet(() -> {
                    Dictionary d = new Dictionary();
                    d.setWord(word);
                    d.setMeaning(lookup.getMeaning());
                    d.setPhonetic(lookup.getPhonetic());
                    d.setPos(lookup.getPos());
                    return dictionaryRepository.save(d);
                });
            } catch (Exception e) {
                log.warn("Dictionary lookup failed for '{}': {}", word, e.getMessage());
                Dictionary d = new Dictionary();
                d.setWord(word);
                return dictionaryRepository.save(d);
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
                .collectionName(v.getVocabList() != null ? v.getVocabList().getTitle() : null)
                .nextReviewAt(v.getNextReviewAt())
                .createdAt(v.getCreatedAt())
                .build();
    }
}

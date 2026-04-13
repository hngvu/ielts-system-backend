package io.gsp26se16.moni.vocab.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.gsp26se16.moni.authentication.entity.Users;
import io.gsp26se16.moni.common.exception.AppException;
import io.gsp26se16.moni.common.exception.ErrorCode;
import io.gsp26se16.moni.vocab.dto.CreateVocabListRequest;
import io.gsp26se16.moni.vocab.dto.VocabListResponse;
import io.gsp26se16.moni.vocab.entity.Vocab;
import io.gsp26se16.moni.vocab.entity.VocabList;
import io.gsp26se16.moni.vocab.enumeration.VocabListType;
import io.gsp26se16.moni.vocab.enumeration.VocabStatus;
import io.gsp26se16.moni.vocab.repository.VocabListRepository;
import io.gsp26se16.moni.vocab.repository.VocabRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VocabListServiceImpl implements VocabListService {

    private final VocabListRepository vocabListRepository;
    private final VocabRepository vocabRepository;
    private final VocabAuthHelper authHelper;

    @Override
    public List<VocabListResponse> getUserLists(String credentialId) {
        Users user = authHelper.getUser(credentialId);
        ensureDefaultList(user);
        List<VocabList> lists = vocabListRepository.findByUserId(user.getId());
        // Exclude the default "Sổ tay của tôi" list — it is represented as a system notebook on the frontend
        return lists.stream()
                .filter(l -> !l.isDefault())
                .map(l -> toResponse(l, user.getId()))
                .toList();
    }

    @Override
    @Transactional
    public VocabListResponse createList(String credentialId, CreateVocabListRequest request) {
        Users user = authHelper.getUser(credentialId);
        VocabList list = VocabList.builder()
                .title(request.getTitle())
                .icon(request.getIcon())
                .description(request.getDescription())
                .type(VocabListType.CUSTOM)
                .isDefault(false)
                .user(user)
                .build();
        return toResponse(vocabListRepository.save(list), user.getId());
    }

    @Override
    @Transactional
    public VocabListResponse updateList(String credentialId, Integer listId, CreateVocabListRequest request) {
        Users user = authHelper.getUser(credentialId);
        VocabList list = vocabListRepository
                .findByIdAndUserId(listId, user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.VOCAB_COLLECTION_NOT_FOUND));
        if (request.getTitle() != null) list.setTitle(request.getTitle());
        if (request.getIcon() != null) list.setIcon(request.getIcon());
        if (request.getDescription() != null) list.setDescription(request.getDescription());
        return toResponse(vocabListRepository.save(list), user.getId());
    }

    @Override
    @Transactional
    public void deleteList(String credentialId, Integer listId) {
        Users user = authHelper.getUser(credentialId);
        VocabList list = vocabListRepository
                .findByIdAndUserId(listId, user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.VOCAB_COLLECTION_NOT_FOUND));
        if (list.isDefault()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        // Move orphaned words to default list
        VocabList defaultList = ensureDefaultList(user);
        List<Vocab> words = vocabRepository.findByUserIdAndVocabListId(user.getId(), listId);
        words.forEach(w -> w.setVocabList(defaultList));
        vocabRepository.saveAll(words);
        vocabListRepository.delete(list);
    }

    // Ensure default list exists; create if not. Returns the default list.
    VocabList ensureDefaultList(Users user) {
        return vocabListRepository.findByUserIdAndIsDefaultTrue(user.getId()).orElseGet(() -> {
            VocabList def = VocabList.builder()
                    .title("Sổ tay của tôi")
                    .icon("📒")
                    .type(VocabListType.CUSTOM)
                    .isDefault(true)
                    .user(user)
                    .build();
            return vocabListRepository.save(def);
        });
    }

    private VocabListResponse toResponse(VocabList list, String userId) {
        long count =
                vocabRepository.countByUserIdAndVocabListIdAndStatusNot(userId, list.getId(), VocabStatus.ARCHIVED);
        return VocabListResponse.builder()
                .id(list.getId())
                .title(list.getTitle())
                .icon(list.getIcon())
                .description(list.getDescription())
                .type(list.getType())
                .isDefault(list.isDefault())
                .wordCount((int) count)
                .createdAt(list.getCreatedAt())
                .build();
    }
}

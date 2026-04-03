package rs.raf.banka2_bek.card.service;

import rs.raf.banka2_bek.card.dto.CardResponseDto;
import rs.raf.banka2_bek.card.dto.CreateCardRequestDto;
import rs.raf.banka2_bek.card.model.CardType;

import java.math.BigDecimal;
import java.util.List;

public interface CardService {
    CardResponseDto createCard(CreateCardRequestDto request);
    CardResponseDto createCardForAccount(Long accountId, Long clientId, BigDecimal limit, CardType cardType);
    List<CardResponseDto> getMyCards();
    List<CardResponseDto> getCardsByAccount(Long accountId);
    CardResponseDto blockCard(Long cardId);
    CardResponseDto unblockCard(Long cardId);
    CardResponseDto deactivateCard(Long cardId);
    CardResponseDto updateCardLimit(Long cardId, BigDecimal newLimit);
}

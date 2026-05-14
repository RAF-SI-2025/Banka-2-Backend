package rs.raf.banka2_bek.savings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.savings.dto.SavingsDepositDto;
import rs.raf.banka2_bek.savings.entity.SavingsDepositStatus;
import rs.raf.banka2_bek.savings.mapper.SavingsMapper;
import rs.raf.banka2_bek.savings.repository.SavingsDepositRepository;

@Service
@RequiredArgsConstructor
public class SavingsAdminService {

    private final SavingsDepositRepository depositRepo;
    private final SavingsMapper mapper;

    @Transactional(readOnly = true)
    public Page<SavingsDepositDto> listAll(SavingsDepositStatus status, Long clientId, Pageable pageable) {
        return depositRepo.adminFindAll(status, clientId, pageable).map(mapper::toDepositDto);
    }
}

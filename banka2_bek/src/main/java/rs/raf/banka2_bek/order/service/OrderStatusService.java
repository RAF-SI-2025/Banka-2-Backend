package rs.raf.banka2_bek.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.order.model.OrderStatus;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderStatusService {

    private final ActuaryInfoRepository actuaryInfoRepository;

    /**
     * Determines the initial status of an order based on who is creating it.
     *
     * CLIENT → APPROVED
     * EMPLOYEE who is SUPERVISOR → APPROVED
     * EMPLOYEE who is AGENT with needApproval=true → PENDING
     * EMPLOYEE who is AGENT over dailyLimit → PENDING
     * EMPLOYEE who is AGENT under dailyLimit → APPROVED
     */
    public OrderStatus determineStatus(String userRole, Long userId, BigDecimal approximatePrice) {
        if ("CLIENT".equals(userRole)) {
            return OrderStatus.APPROVED;
        }

        // EMPLOYEE: check ActuaryInfo
        Optional<ActuaryInfo> actuaryOpt = actuaryInfoRepository.findByEmployeeId(userId);
        if (actuaryOpt.isEmpty()) {
            // Employee without ActuaryInfo — treat as SUPERVISOR (no approval needed)
            return OrderStatus.APPROVED;
        }

        ActuaryInfo actuary = actuaryOpt.get();
        if (actuary.getActuaryType() == ActuaryType.SUPERVISOR) {
            return OrderStatus.APPROVED;
        }

        // AGENT logic
        if (actuary.isNeedApproval()) {
            return OrderStatus.PENDING;
        }

        BigDecimal usedLimit = actuary.getUsedLimit() != null ? actuary.getUsedLimit() : BigDecimal.ZERO;
        BigDecimal dailyLimit = actuary.getDailyLimit() != null ? actuary.getDailyLimit() : BigDecimal.ZERO;

        if (usedLimit.add(approximatePrice).compareTo(dailyLimit) > 0) {
            return OrderStatus.PENDING;
        }

        return OrderStatus.APPROVED;
    }

    /**
     * Returns the ActuaryInfo for an AGENT employee if they exist, otherwise empty.
     * Used by OrderServiceImpl to update usedLimit after an APPROVED order.
     */
    public Optional<ActuaryInfo> getAgentInfo(Long userId) {
        return actuaryInfoRepository.findByEmployeeId(userId);
    }
}

package rs.raf.banka2_bek.investmentfund.mapper;

import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.*;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;

import java.math.BigDecimal;
import java.util.List;

public final class InvestmentFundMapper {

    private InvestmentFundMapper() {}

    public static InvestmentFundDetailDto toDetailDto(InvestmentFund fund,
                                                      Account account,
                                                      BigDecimal fundValue,
                                                      BigDecimal profit,
                                                      List<FundHoldingDto> holdings,
                                                      List<FundPerformancePointDto> performance,
                                                      String managerName) {
        return new InvestmentFundDetailDto(
                fund.getId(),
                fund.getName(),
                fund.getDescription(),
                managerName,
                fund.getManagerEmployeeId(),
                fundValue,
                account.getBalance(),
                profit,
                fund.getMinimumContribution(),
                account.getAccountNumber(),
                holdings,
                performance,
                fund.getInceptionDate());
    }

    public static InvestmentFundSummaryDto toSummaryDto(InvestmentFund fund,
                                                        BigDecimal fundValue,
                                                        BigDecimal profit,
                                                        String managerName) {
        return new InvestmentFundSummaryDto(
                fund.getId(),
                fund.getName(),
                fund.getDescription(),
                fund.getMinimumContribution(),
                fundValue,
                profit,
                managerName,
                fund.getInceptionDate());
    }
}

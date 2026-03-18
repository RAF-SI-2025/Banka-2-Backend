package rs.raf.banka2_bek.account.dto;

import java.util.Random;

public class AccountNumberUtils {

    public static String generate(String typeDigits, String bankCode, String branchCode) {
        Random random = new Random();

        while (true) {
            // Generiše 9 random cifara (npr. 047291032)
            String randomPart = String.format("%09d", random.nextInt(1000000000));
            String candidate = bankCode + branchCode + randomPart + typeDigits;

            if (isValidMod11(candidate)) {
                return candidate;
            }
        }
    }

    private static boolean isValidMod11(String accountNumber) {
        int sum = 0;
        for (char c : accountNumber.toCharArray()) {
            sum += Character.getNumericValue(c);
        }
        return sum % 11 == 0;
    }
}
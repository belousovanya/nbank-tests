package generators;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

public class RandomData {
    private RandomData() {
    }

    public static String getUsername() {
        return RandomStringUtils.randomAlphabetic(10);
    }

    public static String getPassword() {
        return RandomStringUtils.randomAlphabetic(3).toUpperCase() +
                RandomStringUtils.randomAlphabetic(5).toLowerCase() +
                RandomStringUtils.randomNumeric(3) + "$";
    }

    public static BigDecimal getAmount(BigDecimal min, BigDecimal max) {
        long minCents = min.movePointRight(2).longValueExact();
        long maxCents = max.movePointRight(2).longValueExact();
        long cents = ThreadLocalRandom.current().nextLong(minCents, maxCents + 1);
        return BigDecimal.valueOf(cents, 2);
    }

    public static String getName() {
        return getNamePart() + " " + getNamePart();
    }

    public static String getNamePart() {
        return StringUtils.capitalize(RandomStringUtils.randomAlphabetic(3, 10).toLowerCase());
    }
}

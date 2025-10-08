package su.nightexpress.excellentjobs.booster;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.nightcore.bridge.currency.Currency;
import su.nightexpress.excellentjobs.booster.config.BoosterConfig;
import su.nightexpress.excellentjobs.booster.impl.BoosterSchedule;
import su.nightexpress.excellentjobs.util.JobUtils;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

public class BoosterUtils {

    @NotNull
    public static Map<String, BoosterSchedule> getDefaultBoosterSchedules() {
        Map<String, BoosterSchedule> map = new HashMap<>();

        Map<DayOfWeek, LocalTime> dayTimes = new HashMap<>();
        LocalTime time = LocalTime.of(12, 0);
        dayTimes.put(DayOfWeek.SATURDAY, time);
        dayTimes.put(DayOfWeek.SUNDAY, time);

        map.put("weekend", new BoosterSchedule(dayTimes, 1.5, 1.5, 3600 * 6));

        return map;
    }

    public static boolean isBoostable(@NotNull Currency currency) {
        return !BoosterConfig.EXCLUSIVE_CURRENCIES.get().contains(currency.getInternalId());
    }

    public static double getAsPercent(double multiplier) {
        return multiplier * 100D - 100D;
    }

    @NotNull
    public static String formatMultiplier(double multiplier) {
        return JobUtils.formatBonus(getAsPercent(multiplier));
    }
}

package com.hnp.privatechatbot.util;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Converts Gregorian dates to Solar Hijri (Shamsi/Jalali) with Persian digits.
 * Registered as a Spring bean so Thymeleaf templates can call it via ${@shamsi.format(date)}.
 */
@Component("shamsi")
public class ShamsiDateUtil {

    public String format(LocalDateTime dt) {
        if (dt == null) return "";
        int[] j = toJalali(dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth());
        String s = String.format("%04d/%02d/%02d - %02d:%02d", j[0], j[1], j[2], dt.getHour(), dt.getMinute());
        return toPersianDigits(s);
    }

    public String formatTime(LocalDateTime dt) {
        if (dt == null) return "";
        return toPersianDigits(String.format("%02d:%02d", dt.getHour(), dt.getMinute()));
    }

    /**
     * Standard Gregorian-to-Jalali conversion algorithm (same as jalaali-js).
     * Returns [year, month, day] in Jalali calendar.
     */
    private int[] toJalali(int gy, int gm, int gd) {
        int[] gDayNo = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int[] jDayNo = {0, 31, 62, 93, 124, 155, 186, 216, 246, 276, 306, 337};

        int gy2 = (gm > 2) ? gy + 1 : gy;
        int dayNo = 365 * (gy - 1600)
                + (gy2 - 1601) / 4
                - (gy2 - 1) / 100
                + (gy2 + 299) / 400
                + gDayNo[gm - 1] + gd - 1;
        if (gm > 2 && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) dayNo++;

        int jDay = dayNo - 79;
        int np = jDay / 12053;
        jDay %= 12053;

        int jy = 979 + 33 * np + 4 * (jDay / 1461);
        jDay %= 1461;

        if (jDay >= 366) {
            jy += (jDay - 1) / 365;
            jDay = (jDay - 1) % 365;
        }

        int jm = 0;
        while (jm < 11 && jDay >= jDayNo[jm + 1]) jm++;
        int jd = jDay - jDayNo[jm] + 1;

        return new int[]{jy, jm + 1, jd};
    }

    private String toPersianDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return sb.toString();
    }
}

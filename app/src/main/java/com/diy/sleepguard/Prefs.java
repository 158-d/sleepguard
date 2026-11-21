package com.diy.sleepguard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * 配置读写。所有配置项都有默认值，装上就能用。
 */
public class Prefs {

    /** 预置的"会自动连播"的常见 App：{包名, 显示名}。 */
    public static final String[][] KNOWN_APPS = {
            {"com.ss.android.ugc.aweme", "抖音"},
            {"com.ss.android.ugc.aweme.lite", "抖音极速版"},
            {"com.ss.android.ugc.live", "抖音火山版"},
            {"com.smile.gifmaker", "快手"},
            {"tv.danmaku.bili", "哔哩哔哩"},
            {"com.tencent.mm", "微信（视频号）"},
    };

    private static final String FILE = "sleep_guard";
    private static final String K_ENABLED = "enabled";
    private static final String K_MINUTES = "minutes";
    private static final String K_NIGHT_ONLY = "night_only";
    private static final String K_START_HOUR = "start_hour";
    private static final String K_END_HOUR = "end_hour";
    private static final String K_WARN_VIBRATE = "warn_vibrate";
    private static final String K_TARGETS = "targets";
    private static final String K_LOCK_COUNT = "lock_count";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return sp.getBoolean(K_ENABLED, false);
    }

    public void setEnabled(boolean value) {
        sp.edit().putBoolean(K_ENABLED, value).apply();
    }

    /** 连续多少分钟没有任何操作就认为睡着了。 */
    public int getIdleMinutes() {
        return sp.getInt(K_MINUTES, 12);
    }

    public void setIdleMinutes(int minutes) {
        sp.edit().putInt(K_MINUTES, minutes).apply();
    }

    public boolean isNightOnly() {
        return sp.getBoolean(K_NIGHT_ONLY, true);
    }

    public void setNightOnly(boolean value) {
        sp.edit().putBoolean(K_NIGHT_ONLY, value).apply();
    }

    /** 夜间时间窗起点：默认 0 点（即 00:00 → 08:00）。 */
    public int getStartHour() {
        return sp.getInt(K_START_HOUR, 0);
    }

    public void setStartHour(int hour) {
        sp.edit().putInt(K_START_HOUR, hour).apply();
    }

    public int getEndHour() {
        return sp.getInt(K_END_HOUR, 8);
    }

    public void setEndHour(int hour) {
        sp.edit().putInt(K_END_HOUR, hour).apply();
    }

    public boolean isWarnVibrate() {
        return sp.getBoolean(K_WARN_VIBRATE, true);
    }

    public void setWarnVibrate(boolean value) {
        sp.edit().putBoolean(K_WARN_VIBRATE, value).apply();
    }

    public Set<String> getTargets() {
        Set<String> saved = sp.getStringSet(K_TARGETS, null);
        if (saved == null) {
            Set<String> defaults = new HashSet<>();
            defaults.add("com.ss.android.ugc.aweme");
            return defaults;
        }
        return saved;
    }

    public void setTarget(String pkg, boolean on) {
        Set<String> next = new HashSet<>(getTargets());
        if (on) {
            next.add(pkg);
        } else {
            next.remove(pkg);
        }
        sp.edit().putStringSet(K_TARGETS, next).apply();
    }

    /** 当前是否落在"夜间"时间窗内（支持 00:00 → 08:00 这种跨零点写法）。 */
    public boolean inNightWindow() {
        int now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int start = getStartHour();
        int end = getEndHour();
        if (start == end) {
            return true;
        }
        if (start < end) {
            return now >= start && now < end;
        }
        return now >= start || now < end;
    }

    public long getLockCount() {
        return sp.getLong(K_LOCK_COUNT, 0L);
    }

    public void bumpLockCount() {
        sp.edit().putLong(K_LOCK_COUNT, getLockCount() + 1L).apply();
    }
}

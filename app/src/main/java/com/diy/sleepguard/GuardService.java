package com.diy.sleepguard;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 核心服务。
 *
 * 逻辑只有三句话：
 *  1. 每隔 10 秒看一眼：现在前台是哪个 App；
 *  2. 如果前台是抖音（可在设置里勾选），并且"距离上一次用户操作"已经超过阈值；
 *  3. 就调用系统接口直接锁屏（屏幕熄灭，播放随之停止）。
 *
 * "用户操作"来自无障碍事件：点击、长按、滑动、触摸开始、切换 App。
 * 抖音在自动连播时不会产生这些事件，所以能准确区分"人在看"和"人睡着了"。
 */
public class GuardService extends AccessibilityService {

    private static final String TAG = "SleepGuard";
    private static final long TICK_MS = 10_000L;

    /** 给界面层用的句柄（用于"立即测试锁屏"）。 */
    public static volatile GuardService instance;

    private Prefs prefs;
    private PowerManager powerManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long lastInteractionAt = SystemClock.elapsedRealtime();
    private long eventCount = 0L;
    private String foregroundPkg = "";
    private boolean warnSent = false;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            try {
                checkOnce();
            } catch (Throwable t) {
                Log.w(TAG, "checkOnce failed", t);
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        prefs = new Prefs(getApplicationContext());
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        lastInteractionAt = SystemClock.elapsedRealtime();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, TICK_MS);
        Log.i(TAG, "service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || prefs == null) {
            return;
        }
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
            case AccessibilityEvent.TYPE_GESTURE_DETECTION_START:
            case AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START:
                markInteraction();
                break;

            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
                CharSequence pkg = event.getPackageName();
                if (pkg != null) {
                    String name = pkg.toString();
                    boolean isSystem = name.equals("com.android.systemui")
                            || name.equals(getPackageName());
                    if (!isSystem && !name.equals(foregroundPkg)) {
                        // 切到别的 App，本身就是一次用户操作
                        foregroundPkg = name;
                        markInteraction();
                    }
                }
                break;
            }

            default:
                break;
        }
    }

    @Override
    public void onInterrupt() {
        // 系统中断无障碍反馈时无需处理
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopTicker();
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        stopTicker();
        instance = null;
        super.onDestroy();
    }

    /** 立刻锁屏息屏（也被界面上的"测试"按钮调用）。 */
    public void lockNow() {
        if (prefs == null) {
            prefs = new Prefs(getApplicationContext());
        }
        lastInteractionAt = SystemClock.elapsedRealtime();
        warnSent = false;

        boolean ok;
        try {
            ok = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        } catch (Throwable t) {
            Log.w(TAG, "performGlobalAction failed", t);
            ok = false;
        }
        if (ok) {
            prefs.bumpLockCount();
            Log.i(TAG, "screen locked");
        } else {
            Log.w(TAG, "lock failed (system refused)");
        }
    }

    public long getEventCount() {
        return eventCount;
    }

    // ---------------------------------------------------------------- 内部逻辑

    private void checkOnce() {
        if (prefs == null) {
            return;
        }
        final long now = SystemClock.elapsedRealtime();

        // 已经息屏：不计时，等下次亮屏重新开始
        if (powerManager != null && !powerManager.isInteractive()) {
            lastInteractionAt = now;
            warnSent = false;
            return;
        }

        updateForeground();

        if (!prefs.isEnabled()) {
            // 关掉守护时不累计时间，重新打开后从零开始
            lastInteractionAt = now;
            warnSent = false;
            return;
        }

        // 只统计"在目标 App 里持续静止"的时间；去了别的 App 就清零
        if (!prefs.getTargets().contains(foregroundPkg)) {
            lastInteractionAt = now;
            warnSent = false;
            return;
        }

        if (prefs.isNightOnly() && !prefs.inNightWindow()) {
            lastInteractionAt = now;
            warnSent = false;
            return;
        }

        final long limitMs = prefs.getIdleMinutes() * 60_000L;
        final long idleMs = now - lastInteractionAt;

        if (idleMs >= limitMs) {
            lockNow();
            return;
        }

        // 阈值前 1/5 轻震一下：还醒着就动动手，计时会重置
        if (prefs.isWarnVibrate() && !warnSent && idleMs >= limitMs * 4L / 5L) {
            warnSent = true;
            vibrate(80);
        }
    }

    private void updateForeground() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) {
                return;
            }
            CharSequence pkg = root.getPackageName();
            if (pkg == null) {
                return;
            }
            String name = pkg.toString();
            if (name.length() == 0 || name.equals(getPackageName())) {
                return;
            }
            foregroundPkg = name;
        } catch (Throwable t) {
            Log.w(TAG, "updateForeground failed", t);
        } finally {
            if (root != null) {
                root.recycle();
            }
        }
    }

    private void markInteraction() {
        lastInteractionAt = SystemClock.elapsedRealtime();
        warnSent = false;
        eventCount++;
    }

    private void stopTicker() {
        handler.removeCallbacks(ticker);
    }

    @SuppressWarnings("deprecation")
    private void vibrate(int millis) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(millis);
            }
        } catch (Throwable ignored) {
            // 没有振动马达就跳过
        }
    }
}

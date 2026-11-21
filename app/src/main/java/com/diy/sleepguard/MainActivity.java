package com.diy.sleepguard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

/**
 * 唯一的界面：三个按钮（开无障碍 / 测试锁屏 / 关省电）+ 判定条件 + 监视哪些 App。
 * 全部用代码搭 UI，不依赖任何第三方库，保证能直接编译。
 */
public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final int[] MINUTE_OPTIONS = {5, 8, 10, 12, 15, 20, 30, 45, 60};
    private static final int[] HOUR_OPTIONS = new int[24];

    static {
        for (int i = 0; i < HOUR_OPTIONS.length; i++) {
            HOUR_OPTIONS[i] = i;
        }
    }

    private Prefs prefs;
    private TextView statusView;
    private TextView statsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#F5F5F7"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(40));
        scroll.addView(root, new ViewGroup.LayoutParams(MATCH, WRAP));
        setContentView(scroll);

        root.addView(bigTitle("睡着自动息屏"));
        root.addView(paragraph("在抖音里连续若干分钟没有点击、滑动，就自动锁屏息屏，"
                + "避免视频自动连播一整夜。"));

        // ---- 状态 ----
        statusView = paragraph("");
        root.addView(statusView);

        Button accButton = new Button(this);
        accButton.setText("① 打开无障碍权限设置");
        accButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });
        root.addView(accButton);

        Button testButton = new Button(this);
        testButton.setText("② 立即测试一次锁屏");
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testLockNow();
            }
        });
        root.addView(testButton);

        Button batteryButton = new Button(this);
        batteryButton.setText("③ 关掉本 App 的省电限制（防被系统杀掉）");
        batteryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAppDetails();
            }
        });
        root.addView(batteryButton);

        // ---- 总开关 ----
        Switch enableSwitch = new Switch(this);
        enableSwitch.setText("开启守护");
        enableSwitch.setTextSize(16f);
        enableSwitch.setChecked(prefs.isEnabled());
        enableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.setEnabled(isChecked);
            }
        });
        root.addView(enableSwitch);

        // ---- 阈值 ----
        root.addView(sectionTitle("判定条件"));
        root.addView(paragraph("多久没操作算睡着："));
        root.addView(buildSpinner(
                labelsMinutes(), MINUTE_OPTIONS, indexOf(MINUTE_OPTIONS, prefs.getIdleMinutes()),
                new OnValuePicked() {
                    @Override
                    public void onPicked(int value) {
                        prefs.setIdleMinutes(value);
                    }
                }));

        CheckBox nightBox = new CheckBox(this);
        nightBox.setText("只在夜间生效");
        nightBox.setTextSize(15f);
        nightBox.setChecked(prefs.isNightOnly());
        nightBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.setNightOnly(isChecked);
            }
        });
        root.addView(nightBox);

        LinearLayout hoursRow = new LinearLayout(this);
        hoursRow.setOrientation(LinearLayout.HORIZONTAL);
        hoursRow.setGravity(Gravity.CENTER_VERTICAL);
        hoursRow.setPadding(0, dp(8), 0, dp(8));

        TextView fromLabel = paragraph("从 ");
        hoursRow.addView(fromLabel, new LinearLayout.LayoutParams(WRAP, WRAP));
        hoursRow.addView(buildSpinner(
                labelsHours(), HOUR_OPTIONS, indexOf(HOUR_OPTIONS, prefs.getStartHour()),
                new OnValuePicked() {
                    @Override
                    public void onPicked(int value) {
                        prefs.setStartHour(value);
                    }
                }), new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView toLabel = paragraph("  到 ");
        hoursRow.addView(toLabel, new LinearLayout.LayoutParams(WRAP, WRAP));
        hoursRow.addView(buildSpinner(
                labelsHours(), HOUR_OPTIONS, indexOf(HOUR_OPTIONS, prefs.getEndHour()),
                new OnValuePicked() {
                    @Override
                    public void onPicked(int value) {
                        prefs.setEndHour(value);
                    }
                }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        root.addView(hoursRow);

        CheckBox vibrateBox = new CheckBox(this);
        vibrateBox.setText("锁定前轻震提醒（还醒着就动一下手指）");
        vibrateBox.setTextSize(15f);
        vibrateBox.setChecked(prefs.isWarnVibrate());
        vibrateBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.setWarnVibrate(isChecked);
            }
        });
        root.addView(vibrateBox);

        // ---- 监视哪些 App ----
        root.addView(sectionTitle("监视哪些 App"));
        root.addView(paragraph("勾选之后，在这些 App 里长时间无操作同样会自动息屏。"));
        final Set<String> targets = prefs.getTargets();
        for (int i = 0; i < Prefs.KNOWN_APPS.length; i++) {
            final String pkg = Prefs.KNOWN_APPS[i][0];
            final String name = Prefs.KNOWN_APPS[i][1];
            CheckBox box = new CheckBox(this);
            box.setText(name);
            box.setTextSize(15f);
            box.setChecked(targets.contains(pkg));
            box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    prefs.setTarget(pkg, isChecked);
                }
            });
            root.addView(box);
        }

        // ---- 统计 ----
        root.addView(sectionTitle("运行情况"));
        statsView = paragraph("");
        root.addView(statsView);

        root.addView(sectionTitle("使用说明"));
        root.addView(paragraph(
                "1. 点 ①，在无障碍列表里找到「睡着自动息屏」并打开。\n"
                        + "2. 回到这个界面，看到「运行中」就说明生效了。\n"
                        + "3. 建议点 ② 试一次锁屏，确认系统允许本 App 锁屏。\n"
                        + "4. 重要：去抖音设置里关掉「后台播放 / 锁屏后继续播放」，"
                        + "否则屏幕黑了声音还会响。\n"
                        + "5. 部分手机（小米、华为、OPPO、vivo）需要在系统管家里允许本 App 自启动、"
                        + "并把它设为「无限制」省电，否则服务会被杀掉。"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        GuardService service = GuardService.instance;
        boolean running = service != null;

        if (statusView != null) {
            if (running) {
                statusView.setText("守护服务：运行中 ✅");
                statusView.setTextColor(Color.parseColor("#1E7B34"));
            } else {
                statusView.setText("守护服务：未开启 ❌  点下面 ① 到无障碍设置里打开它。");
                statusView.setTextColor(Color.parseColor("#B0261E"));
            }
        }
        if (statsView != null) {
            String text = "已自动息屏 " + prefs.getLockCount() + " 次";
            if (running) {
                text = text + "；本次运行已识别 " + service.getEventCount() + " 次操作";
            }
            statsView.setText(text);
        }
    }

    // ------------------------------------------------------------ 界面小工具

    private interface OnValuePicked {
        void onPicked(int value);
    }

    private Spinner buildSpinner(String[] labels, final int[] values, int selectedIndex,
                                 final OnValuePicked callback) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (selectedIndex >= 0 && selectedIndex < labels.length) {
            spinner.setSelection(selectedIndex);
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < values.length) {
                    callback.onPicked(values[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        return spinner;
    }

    private String[] labelsMinutes() {
        String[] labels = new String[MINUTE_OPTIONS.length];
        for (int i = 0; i < MINUTE_OPTIONS.length; i++) {
            labels[i] = MINUTE_OPTIONS[i] + " 分钟";
        }
        return labels;
    }

    private String[] labelsHours() {
        String[] labels = new String[HOUR_OPTIONS.length];
        for (int i = 0; i < HOUR_OPTIONS.length; i++) {
            labels[i] = (HOUR_OPTIONS[i] < 10 ? "0" : "") + HOUR_OPTIONS[i] + ":00";
        }
        return labels;
    }

    private int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return 0;
    }

    private TextView bigTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(23f);
        view.setTextColor(Color.parseColor("#1B1B1F"));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15f);
        view.setTextColor(Color.parseColor("#1B1B1F"));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(22), 0, dp(6));
        return view;
    }

    private TextView paragraph(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14f);
        view.setTextColor(Color.parseColor("#5A5A62"));
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ------------------------------------------------------------ 跳转

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "在列表里找到「睡着自动息屏」并打开", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "打不开无障碍设置，请手动到 设置 → 无障碍 里打开",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void testLockNow() {
        GuardService service = GuardService.instance;
        if (service == null) {
            Toast.makeText(this, "请先打开无障碍权限，再测试", Toast.LENGTH_LONG).show();
            return;
        }
        service.lockNow();
    }

    private void openAppDetails() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "打不开应用详情，请手动到 设置 → 应用 里找",
                    Toast.LENGTH_LONG).show();
        }
    }
}

package com.cleantv.launcher.childlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.cleantv.launcher.R;
import com.cleantv.launcher.Typefaces;

import java.util.List;

/**
 * 电视遥控器端：儿童模式与家长设置中心
 */
public class ChildLockSettingsActivity extends Activity {

    private ChildLockStore store;
    private TextView tvMasterSwitchStatus;
    private TextView tvCurrentModeStatus;
    private TextView tvAntiTamperStatus;
    private TextView tvTodayWatched;
    private LinearLayout layoutRulesContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_lock_settings);
        Typefaces.apply(findViewById(android.R.id.content));

        store = ChildLockStore.getInstance(this);

        initViews();
        refreshStatus();
    }

    private void initViews() {
        tvMasterSwitchStatus = findViewById(R.id.tv_master_switch_status);
        tvCurrentModeStatus = findViewById(R.id.tv_current_mode_status);
        tvAntiTamperStatus = findViewById(R.id.tv_anti_tamper_status);
        tvTodayWatched = findViewById(R.id.tv_today_watched);
        layoutRulesContainer = findViewById(R.id.layout_rules_container);

        // 1. 儿童锁总开关
        findViewById(R.id.row_master_switch).setOnClickListener(v -> {
            boolean cur = store.isChildLockEnabled();
            store.setChildLockEnabled(!cur);
            refreshStatus();
            Toast.makeText(this, !cur ? "儿童锁已开启" : "儿童锁已停用", Toast.LENGTH_SHORT).show();
        });

        // 2. 当前模式手动切换
        findViewById(R.id.row_switch_mode).setOnClickListener(v -> {
            String curMode = store.getCurrentSessionMode();
            if (ChildLockStore.MODE_ADULT.equals(curMode)) {
                store.setCurrentSessionMode(ChildLockStore.MODE_CHILD);
                Toast.makeText(this, "已切换为儿童模式", Toast.LENGTH_SHORT).show();
            } else {
                store.setCurrentSessionMode(ChildLockStore.MODE_ADULT);
                Toast.makeText(this, "已切换为大人模式", Toast.LENGTH_SHORT).show();
            }
            refreshStatus();
        });

        // 3. 防篡改开关（默认开）
        findViewById(R.id.row_anti_tamper).setOnClickListener(v -> {
            boolean cur = store.isAntiTamperEnabled();
            store.setAntiTamperEnabled(!cur);
            refreshStatus();
            Toast.makeText(this, !cur ? "时间防篡改保护已开启" : "时间防篡改保护已关闭", Toast.LENGTH_SHORT).show();
        });

        // 4. 修改 4 位家长密码
        findViewById(R.id.row_change_password).setOnClickListener(v -> showChangePasswordDialog());

        // 5. 重置今日已看时长与冷却
        findViewById(R.id.row_reset_cooldown).setOnClickListener(v -> {
            store.clearCooldown();
            store.clearTempUnlock();
            Toast.makeText(this, "已清除冷却限制与临时放行", Toast.LENGTH_SHORT).show();
            refreshStatus();
        });

        // 6. 返回按钮
        findViewById(R.id.btn_close_settings).setOnClickListener(v -> finish());
    }

    private void refreshStatus() {
        boolean enabled = store.isChildLockEnabled();
        tvMasterSwitchStatus.setText(enabled ? "已开启（管控时段开机需选模式）" : "已关闭（开机直进大人桌面）");
        tvMasterSwitchStatus.setTextColor(enabled ? 0xFF34C759 : 0xFF888888);

        String mode = store.getCurrentSessionMode();
        if (ChildLockStore.MODE_ADULT.equals(mode)) {
            tvCurrentModeStatus.setText("当前为：👨‍👩‍👧 大人模式（无限制）");
            tvCurrentModeStatus.setTextColor(0xFFFFD54F);
        } else if (ChildLockStore.MODE_CHILD.equals(mode)) {
            tvCurrentModeStatus.setText("当前为：👶 儿童模式（受限时保护）");
            tvCurrentModeStatus.setTextColor(0xFF81D4FA);
        } else {
            tvCurrentModeStatus.setText("未选择（等待开机确认）");
            tvCurrentModeStatus.setTextColor(0xFFB0C4DE);
        }

        boolean anti = store.isAntiTamperEnabled();
        tvAntiTamperStatus.setText(anti ? "已开启（检测时间回拨自动锁屏）" : "已关闭");
        tvAntiTamperStatus.setTextColor(anti ? 0xFF34C759 : 0xFF888888);

        int watchedSec = store.getTodayWatchedSeconds();
        int minutes = watchedSec / 60;
        tvTodayWatched.setText("今日儿童累计观看：" + minutes + " 分钟");

        loadRulesList();
    }

    private void loadRulesList() {
        layoutRulesContainer.removeAllViews();
        List<ChildLockRule> rules = store.getRules();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < rules.size(); i++) {
            final ChildLockRule rule = rules.get(i);
            View itemView = inflater.inflate(R.layout.item_child_rule_summary, layoutRulesContainer, false);
            TextView tvName = itemView.findViewById(R.id.tv_rule_item_name);
            TextView tvTime = itemView.findViewById(R.id.tv_rule_item_time);
            TextView tvLimits = itemView.findViewById(R.id.tv_rule_item_limits);

            tvName.setText("规则 " + (i + 1) + "：" + rule.getName() + (rule.isEnabled() ? " (生效中)" : " (已停用)"));
            tvTime.setText("时段：" + rule.getTimeRangeString());
            tvLimits.setText("单次限时 " + rule.getSingleLimitMinutes() + " 分钟 · 休息 " +
                    rule.getCooldownMinutes() + " 分钟 · 每日上限 " + rule.getDailyLimitMinutes() + " 分钟");

            final int idx = i;
            itemView.setOnClickListener(v -> {
                rule.setEnabled(!rule.isEnabled());
                store.addOrUpdateRule(rule);
                refreshStatus();
                Toast.makeText(this, rule.getName() + (rule.isEnabled() ? " 已启用" : " 已停用"), Toast.LENGTH_SHORT).show();
            });

            layoutRulesContainer.addView(itemView);
        }
    }

    private void showChangePasswordDialog() {
        final EditText et = new EditText(this);
        et.setHint("输入4位新数字密码");
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setTextSize(20f);

        new AlertDialog.Builder(this)
                .setTitle("🔑 修改家长密码")
                .setView(et)
                .setPositiveButton("确定", (dialog, which) -> {
                    String pwd = et.getText().toString().trim();
                    if (pwd.length() == 4) {
                        store.setMasterPassword(pwd);
                        Toast.makeText(this, "密码已修改为: " + pwd, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "密码必须是4位纯数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}

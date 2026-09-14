package ru.bless.offban;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final ArrayList<String> draft = new ArrayList<>();
    private final HashSet<String> copied = new HashSet<>();
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView count;
    private Button clear;
    private Rows adapter;
    private AlertDialog inputDialog;
    private EditText activeInput;
    private TextView draftCount;
    private String reason = "", typed = "";
    private int step = 0;
    private boolean healthyStorage = true;

    private static final int BG = Color.rgb(16, 18, 22);
    private static final int CARD = Color.rgb(26, 30, 37);
    private static final int INK = Color.rgb(245, 246, 250);
    private static final int MUTED = Color.rgb(173, 180, 194);
    private static final int ACCENT = Color.rgb(243, 107, 88);
    private static final Pattern INVALID_NICK = Pattern.compile("[\\s\\p{C}\\p{Z}/\\\\]");

    private static final class Entry {
        final String nick, reason, id;
        Entry(String nick, String reason, String id) {
            this.nick = nick; this.reason = reason; this.id = id;
        }
        String command() { return "/offban " + nick + " 0 " + reason; }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("offban_v1", MODE_PRIVATE);
        load();
        buildScreen();
        if (step != 0 && healthyStorage) root.post(() -> {
            if (!isFinishing() && !isDestroyed()) openStep();
        });
        if (!healthyStorage) {
            new AlertDialog.Builder(this).setTitle("Не удалось прочитать список")
                .setMessage("Сохранённые данные оставлены без изменений. Закройте приложение и попробуйте открыть его снова.")
                .setPositiveButton("Закрыть", (d, w) -> finish())
                .setCancelable(false).show();
        }
    }

    private void buildScreen() {
        root = column(0);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(dp(20) + bars.left, dp(16) + bars.top, dp(20) + bars.right, dp(16) + bars.bottom);
                return insets;
            });
        } else root.setFitsSystemWindows(true);
        setContentView(root);

        TextView brand = text("BLESS / ИНСТРУМЕНТЫ", 12, ACCENT);
        brand.setLetterSpacing(0.10f);
        root.addView(brand);
        TextView title = text("Команды offban", 28, INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        padVertical(title, 12);
        root.addView(title);
        count = text("", 14, MUTED);
        count.setId(R.id.command_count);
        root.addView(count);

        FrameLayout content = new FrameLayout(this);
        LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(-1, 0, 1);
        fill.setMargins(0, dp(20), 0, dp(12));
        root.addView(content, fill);
        ListView list = new ListView(this);
        list.setId(R.id.command_list);
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(8));
        content.addView(list, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = text("Список пока пуст\n\nНажмите «+», укажите причину\nи добавьте ники по очереди.", 17, MUTED);
        empty.setGravity(Gravity.CENTER);
        empty.setLineSpacing(dp(4), 1f);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(empty);
        adapter = new Rows();
        list.setAdapter(adapter);

        Button add = button("+  Добавить ники", true);
        add.setId(R.id.add_group);
        add.setContentDescription("Добавить группу ников");
        add.setOnClickListener(v -> {
            if (!healthyStorage) return;
            reason = ""; typed = ""; draft.clear(); step = 1;
            persist(); openStep();
        });
        root.addView(add, new LinearLayout.LayoutParams(-1, dp(56)));
        clear = button("Очистить список", false);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Очистить список?")
            .setMessage("Все созданные команды будут удалены с этого телефона.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Очистить", (d, w) -> {
                entries.clear(); copied.clear(); persist(); refresh();
            }).show());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(-1, dp(48));
        clearParams.topMargin = dp(8);
        root.addView(clear, clearParams);
        refresh();
        root.requestApplyInsets();
    }

    private void refresh() {
        count.setText("Команд в списке: " + entries.size());
        clear.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void openStep() {
        if (step == 0 || !healthyStorage || inputDialog != null) return;
        final boolean reasonStep = step == 1;
        LinearLayout box = column(22);
        TextView hint = text(reasonStep
            ? "Эта причина будет общей для всех добавляемых ников."
            : "Причина: " + reason, 15, MUTED);
        box.addView(hint);
        if (!reasonStep) {
            draftCount = text("", 14, ACCENT);
            padVertical(draftCount, 10);
            updateDraftCount();
            box.addView(draftCount);
        }
        EditText field = new EditText(this);
        activeInput = field;
        field.setId(R.id.entry_input);
        field.setTextColor(INK);
        field.setTextSize(18);
        field.setHintTextColor(MUTED);
        field.setHint(reasonStep ? "Например: Неуважение к администрации" : "Например: Ivan_Ivanov");
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(reasonStep ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        field.setText(typed);
        field.setSelection(field.length());
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = dp(12);
        box.addView(field, fp);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(reasonStep ? "Причина блокировки" : "Добавить ник")
            .setView(box)
            .setNegativeButton("Отмена", null)
            .setPositiveButton(reasonStep ? "Далее" : "Добавить ещё", null);
        if (!reasonStep) builder.setNeutralButton("Готово", null);
        AlertDialog dialog = builder.create();
        inputDialog = dialog;
        dialog.setCanceledOnTouchOutside(false);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                typed = s.toString();
                field.setError(null);
                persist();
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (reasonStep) acceptReason(); else addNick(false);
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> cancelDraft());
            if (!reasonStep) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> finishDraft());
            field.requestFocus();
            dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE |
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            field.post(() -> {
                if (!dialog.isShowing()) return;
                InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
            });
        });
        field.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_NEXT || action == EditorInfo.IME_ACTION_DONE) {
                if (reasonStep) acceptReason(); else addNick(false);
                return true;
            }
            return false;
        });
        dialog.setOnCancelListener(d -> {
            inputDialog = null; activeInput = null; draftCount = null;
            if (draft.isEmpty()) resetDraft();
            else new AlertDialog.Builder(this).setTitle("Отменить добавление?")
                .setMessage("Уже введено ников: " + draft.size())
                .setPositiveButton("Удалить черновик", (a, b) -> resetDraft())
                .setNegativeButton("Продолжить", (a, b) -> openStep())
                .setOnCancelListener(a -> openStep()).show();
        });
        dialog.show();
    }

    private void acceptReason() {
        String value = typed.replaceAll("[\\s\\p{Z}\\p{Cc}]+", " ").trim();
        if (value.isEmpty()) { activeInput.setError("Введите причину"); return; }
        reason = value; typed = ""; step = 2;
        closeInput(); persist(); openStep();
    }

    private boolean addNick(boolean allowEmpty) {
        String nick = typed.trim();
        if (nick.isEmpty()) {
            if (!allowEmpty) activeInput.setError("Введите ник");
            return allowEmpty;
        }
        if (INVALID_NICK.matcher(nick).find()) {
            activeInput.setError("Введите один ник без пробелов и слешей");
            return false;
        }
        for (String existing : draft) {
            if (existing.equalsIgnoreCase(nick)) {
                activeInput.setError("Этот ник уже добавлен");
                return false;
            }
        }
        draft.add(nick);
        typed = "";
        activeInput.setText("");
        updateDraftCount();
        persist();
        return true;
    }

    private void finishDraft() {
        if (!addNick(true)) return;
        if (draft.isEmpty()) {
            activeInput.setError("Добавьте хотя бы один ник");
            return;
        }
        int added = draft.size();
        for (String nick : draft) entries.add(new Entry(nick, reason, UUID.randomUUID().toString()));
        closeInput(); resetDraft(); refresh();
        Toast.makeText(this, "Добавлено команд: " + added, Toast.LENGTH_SHORT).show();
    }

    private void updateDraftCount() {
        if (draftCount != null) {
            String tail = draft.isEmpty() ? "" : "\nПоследний: " + draft.get(draft.size() - 1);
            draftCount.setText("Добавлено ников: " + draft.size() + tail);
        }
    }

    private void cancelDraft() {
        if (draft.isEmpty()) { closeInput(); resetDraft(); return; }
        new AlertDialog.Builder(this).setTitle("Отменить добавление?")
            .setMessage("Уже введено ников: " + draft.size())
            .setNegativeButton("Продолжить", null)
            .setPositiveButton("Удалить черновик", (d, w) -> { closeInput(); resetDraft(); })
            .show();
    }

    private void closeInput() {
        if (inputDialog != null) inputDialog.dismiss();
        inputDialog = null; activeInput = null; draftCount = null;
    }

    private void resetDraft() {
        step = 0; typed = ""; reason = ""; draft.clear();
        persist();
    }

    private void persist() {
        if (!healthyStorage || prefs == null) return;
        JSONObject state = new JSONObject();
        JSONArray rows = new JSONArray();
        try {
            for (Entry e : entries) rows.put(new JSONObject()
                .put("nick", e.nick).put("reason", e.reason).put("id", e.id));
            state.put("rows", rows).put("step", step).put("reason", reason)
                .put("typed", typed).put("draft", new JSONArray(draft));
            prefs.edit().putString("state", state.toString()).apply();
        } catch (JSONException ex) {
            Toast.makeText(this, "Не удалось сохранить список", Toast.LENGTH_LONG).show();
        }
    }

    private void load() {
        try {
            JSONObject state = new JSONObject(prefs.getString("state", "{}"));
            JSONArray rows = state.optJSONArray("rows");
            if (rows != null) for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                entries.add(new Entry(row.getString("nick"), row.getString("reason"), row.getString("id")));
            }
            step = state.optInt("step", 0);
            if (step < 0 || step > 2) step = 0;
            reason = state.optString("reason", "");
            typed = state.optString("typed", "");
            JSONArray pending = state.optJSONArray("draft");
            if (pending != null) for (int i = 0; i < pending.length(); i++) draft.add(pending.getString(i));
        } catch (JSONException | ClassCastException ex) {
            healthyStorage = false;
        }
    }

    @Override protected void onPause() { persist(); super.onPause(); }
    @Override protected void onDestroy() { closeInput(); super.onDestroy(); }

    private final class Rows extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View reused, ViewGroup parent) {
            Entry entry = entries.get(position);
            LinearLayout outer = column(0);
            outer.setPadding(0, 0, 0, dp(12));
            LinearLayout card = column(16);
            card.setBackground(shape(CARD, Color.rgb(44, 49, 59), 16));
            outer.addView(card);
            TextView name = text((position + 1) + ".  " + entry.nick, 18, INK);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            card.addView(name);
            TextView command = text(entry.command(), 15, MUTED);
            command.setId(R.id.command);
            command.setTypeface(Typeface.MONOSPACE);
            command.setLineSpacing(dp(3), 1f);
            command.setTextIsSelectable(true);
            padVertical(command, 12);
            card.addView(command);
            Button copy = button(copied.contains(entry.id) ? "Скопировано ✓" : "Копировать", false);
            copy.setContentDescription("Копировать команду для " + entry.nick);
            copy.setOnClickListener(v -> {
                try {
                    ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (manager == null) throw new IllegalStateException();
                    manager.setPrimaryClip(ClipData.newPlainText("Команда offban", entry.command()));
                    copied.add(entry.id);
                    copy.setText("Скопировано ✓");
                    if (Build.VERSION.SDK_INT < 33)
                        Toast.makeText(MainActivity.this, "Команда скопирована", Toast.LENGTH_SHORT).show();
                } catch (RuntimeException ex) {
                    Toast.makeText(MainActivity.this, "Не удалось скопировать команду", Toast.LENGTH_SHORT).show();
                }
            });
            card.addView(copy, new LinearLayout.LayoutParams(-1, dp(48)));
            return outer;
        }
    }

    private LinearLayout column(int padding) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        return layout;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button view = new Button(this);
        view.setText(label); view.setAllCaps(false); view.setTextSize(16);
        view.setTextColor(primary ? BG : INK);
        view.setBackground(shape(primary ? ACCENT : CARD,
            primary ? ACCENT : Color.rgb(60, 67, 81), 14));
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setMinHeight(dp(48));
        return view;
    }

    private GradientDrawable shape(int color, int stroke, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(dp(radius)); bg.setStroke(dp(1), stroke);
        return bg;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void padVertical(View view, int amount) { view.setPadding(0, dp(amount), 0, dp(amount)); }
}

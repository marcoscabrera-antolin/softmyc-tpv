package com.softmyc.tpv.presentation;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Checkable;

import androidx.appcompat.widget.AppCompatTextView;

/** Chip ligero: TextView checkable con fondo de píldora por estado; evita el coste de inflado de Material Chip. */
final class LightChipView extends AppCompatTextView implements Checkable {
    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};
    private static final int MIN_TOUCH_DP = 48;       // RNF-04
    private static final int VISUAL_INSET_DP = 8;     // 32dp visibles dentro de 48dp táctiles
    private static final int PADDING_H_DP = 12;
    private static final float CORNER_RADIUS_PX = 18f;
    private static final int STROKE_WIDTH_PX = 3;     // 2.5f del Chip original

    public interface OnCheckedChangeListener {
        void onCheckedChanged(LightChipView view, boolean isChecked);
    }

    private boolean checked;
    private boolean broadcasting;
    private OnCheckedChangeListener checkedListener;

    LightChipView(Context ctx, String text, int activeBg, int activeStroke, int activeText) {
        super(ctx);
        float density = ctx.getResources().getDisplayMetrics().density;
        int minTouch = Math.round(MIN_TOUCH_DP * density);
        int inset = Math.round(VISUAL_INSET_DP * density);
        int padH = Math.round(PADDING_H_DP * density);

        setText(text != null ? text : "");
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setMinHeight(minTouch);
        setMinWidth(minTouch);
        setPadding(padH, 0, padH, 0);
        setClickable(true);
        setFocusable(true);

        int inactiveBg = Color.parseColor("#1E293B");
        int inactiveStroke = Color.parseColor("#334155");
        int inactiveText = Color.parseColor("#E2E8F0");
        int[][] states = {CHECKED_STATE_SET, new int[]{}};

        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(CORNER_RADIUS_PX);
        pill.setColor(new ColorStateList(states, new int[]{activeBg, inactiveBg}));
        pill.setStroke(STROKE_WIDTH_PX, new ColorStateList(states, new int[]{activeStroke, inactiveStroke}));
        setBackground(new InsetDrawable(pill, 0, inset, 0, inset));
        setTextColor(new ColorStateList(states, new int[]{activeText, inactiveText}));
    }

    void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.checkedListener = listener;
    }

    @Override
    public void setChecked(boolean value) {
        if (checked == value) return;
        checked = value;
        refreshDrawableState();
        if (broadcasting) return;
        broadcasting = true;
        try {
            if (checkedListener != null) checkedListener.onCheckedChanged(this, checked);
        } finally {
            broadcasting = false;
        }
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        setChecked(!checked);
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        int[] state = super.onCreateDrawableState(extraSpace + 1);
        if (checked) mergeDrawableStates(state, CHECKED_STATE_SET);
        return state;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return android.widget.CheckBox.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(checked);
    }
}

package arpa.homelab.pathblock;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Design {
    final Context context;
    final int background = Color.rgb(15, 20, 23);
    final int surface = Color.rgb(25, 32, 36);
    final int raised = Color.rgb(37, 48, 52);
    final int foreground = Color.rgb(230, 238, 236);
    final int muted = Color.rgb(174, 190, 186);
    final int danger = Color.rgb(255, 180, 171);
    final int accent;

    Design(Context context) {
        this.context = context;
        accent = Build.VERSION.SDK_INT >= 31
                ? context.getColor(android.R.color.system_accent1_200)
                : Color.rgb(156, 230, 205);
    }

    int dp(int pixels) {
        return Math.round(pixels * context.getResources().getDisplayMetrics().density);
    }

    GradientDrawable shape(int fill, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    RippleDrawable ripple(int fill, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x33ffffff),
                shape(fill, radius), shape(Color.WHITE, radius));
    }

    RippleDrawable outlinedButton() {
        GradientDrawable outline = shape(surface, 24);
        outline.setStroke(dp(1), muted);
        return new RippleDrawable(ColorStateList.valueOf(0x33ffffff),
                outline, shape(Color.WHITE, 24));
    }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    TextView text(String content, int size, int color, boolean strong) {
        TextView view = new TextView(context);
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(strong ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return view;
    }

    Button button(String label, int fill, int color) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(color);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
        button.setBackground(ripple(fill, 24));
        return button;
    }

    ImageButton icon(int resource, int color, String description) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(resource);
        button.setImageTintList(ColorStateList.valueOf(color));
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        button.setPadding(0, 0, 0, 0);
        button.setBackground(ripple(Color.TRANSPARENT, 24));
        button.setContentDescription(description);
        if (Build.VERSION.SDK_INT >= 26) button.setTooltipText(description);
        return button;
    }
}

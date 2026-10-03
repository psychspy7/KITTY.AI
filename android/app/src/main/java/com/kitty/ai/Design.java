package com.kitty.ai;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.*;

/** Quiet navy, warm ivory and champagne. One consistent native design system. */
final class Design {
    static final int BG=0xFF0C101C,CARD=0xFF171E2E,TEXT=0xFFF4F0E8,MUTED=0xFFADB4C5,GOLD=0xFFE8C791,LINE=0xFF30394B;
    final Activity a;
    Design(Activity activity){a=activity;}
    int dp(int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    GradientDrawable surface(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    GradientDrawable outline(int color,int radius){GradientDrawable g=surface(color,radius);g.setStroke(dp(1),LINE);return g;}
    TextView text(String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setLineSpacing(dp(3),1);return t;}
    TextView label(String value){TextView t=text(value,11,GOLD);t.setLetterSpacing(.16f);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    LinearLayout column(){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(android.view.Gravity.CENTER_VERTICAL);return l;}
    void gap(LinearLayout parent,int n){View v=new View(a);parent.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    Button button(String label,boolean primary,Runnable action){
        Button b=new Button(a);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(primary?BG:TEXT);b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x26FFFFFF),surface(primary?GOLD:CARD,16),null));b.setPadding(dp(14),dp(8),dp(14),dp(8));b.setMinHeight(dp(50));b.setOnClickListener(v->action.run());return b;
    }
    EditText field(String hint,String value,boolean secret){EditText e=new EditText(a);e.setText(value);e.setHint(hint);e.setTextSize(15);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setBackground(outline(CARD,14));e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(secret?android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));e.setSaveEnabled(!secret);return e;}
}

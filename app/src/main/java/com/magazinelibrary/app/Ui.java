package com.magazinelibrary.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BG=0xfff7f5ee, INK=0xff203b37, GREEN=0xff14675d, MUTED=0xff73827b, LINE=0xffe1e6df, WHITE=Color.WHITE;
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static TextView text(Context c,String s,int size,int color) { TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t; }
    static TextView heading(Context c,String s,int size) {TextView t=text(c,s,size,INK);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    static LinearLayout col(Context c) {LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout row(Context c) {LinearLayout l=new LinearLayout(c);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static GradientDrawable bg(int color,int radius) {GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
    static Button button(Context c,String s,Runnable action) {Button b=new Button(c);b.setText(s);b.setAllCaps(false);b.setTextColor(GREEN);b.setTextSize(14);b.setMinHeight(dp(c,44));b.setMinimumHeight(dp(c,44));b.setMinWidth(dp(c,64));b.setMinimumWidth(dp(c,64));b.setStateListAnimator(null);b.setBackground(bg(0xffe4efea,dp(c,12)));b.setPadding(dp(c,12),0,dp(c,12),0);b.setOnClickListener(v->action.run());return b;}
    static void pad(View v,int x,int y){v.setPadding(dp(v.getContext(),x),dp(v.getContext(),y),dp(v.getContext(),x),dp(v.getContext(),y));}
    static void space(LinearLayout l,int h){View v=new View(l.getContext());l.addView(v,new LinearLayout.LayoutParams(1,dp(l.getContext(),h)));}
    static String size(long n){if(n<0)return "大小未知";if(n<1024)return n+" B";if(n<1024*1024)return String.format(java.util.Locale.ROOT,"%.1f KB",n/1024.0);return String.format(java.util.Locale.ROOT,"%.1f MB",n/1048576.0);}
}

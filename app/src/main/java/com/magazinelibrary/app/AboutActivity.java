package com.magazinelibrary.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Author, project links, privacy notes and a manual update check. */
public class AboutActivity extends Activity {
    private static final String RELEASES_API =
            "https://api.github.com/repos/kongdayan/magazine-library-android/releases/latest";
    private static final String RELEASES_PAGE =
            "https://github.com/kongdayan/magazine-library-android/releases/latest";

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView updateStatus;
    private boolean checking;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout hero = Ui.col(this);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(hero, 20, 24);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher_foreground);
        icon.setBackground(Ui.bg(Ui.GREEN, Ui.dp(this, 22)));
        hero.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 84), Ui.dp(this, 84)));
        Ui.space(hero, 12);
        TextView name = Ui.heading(this, getString(R.string.app_name), 24);
        name.setGravity(Gravity.CENTER);
        hero.addView(name);
        TextView version = Ui.text(this, getString(R.string.about_version,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), 12, Ui.MUTED);
        version.setGravity(Gravity.CENTER);
        hero.addView(version);
        Ui.space(hero, 8);
        TextView tagline = Ui.text(this, getString(R.string.app_tagline), 14, Ui.INK);
        tagline.setGravity(Gravity.CENTER);
        hero.addView(tagline);
        Ui.space(hero, 10);
        TextView build = Ui.text(this, getString(R.string.about_build,
                BuildConfig.GIT_COMMIT, BuildConfig.BUILD_TIME), 10, Ui.MUTED);
        build.setGravity(Gravity.CENTER);
        hero.addView(build);
        root.addView(hero);

        LinearLayout body = Ui.col(this);
        Ui.pad(body, 16, 0);
        root.addView(body);

        section(body, getString(R.string.about_author_section));
        LinearLayout author = Ui.row(this);
        author.setBackground(Ui.bg(Ui.WHITE, Ui.dp(this, 14)));
        Ui.pad(author, 14, 14);
        TextView monogram = Ui.heading(this, "孔", 24);
        monogram.setGravity(Gravity.CENTER);
        monogram.setTextColor(Ui.WHITE);
        monogram.setBackground(Ui.bg(Ui.GREEN, Ui.dp(this, 28)));
        author.addView(monogram, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        LinearLayout who = Ui.col(this);
        Ui.pad(who, 14, 0);
        who.addView(Ui.heading(this, getString(R.string.about_author_name), 17));
        who.addView(Ui.text(this, getString(R.string.about_author_role), 12, Ui.MUTED));
        TextView bio = Ui.text(this, getString(R.string.about_author_bio), 12, Ui.INK);
        bio.setLineSpacing(Ui.dp(this, 3), 1f);
        Ui.pad(bio, 0, 6);
        who.addView(bio);
        author.addView(who, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(author);
        Ui.space(body, 8);
        link(body, getString(R.string.about_github_profile), "github.com/kongdayan",
                "https://github.com/kongdayan");
        link(body, getString(R.string.about_email), "19552144+kongdayan@users.noreply.github.com",
                "mailto:19552144+kongdayan@users.noreply.github.com");

        section(body, getString(R.string.about_project));
        link(body, getString(R.string.about_source),
                "github.com/kongdayan/magazine-library-android",
                "https://github.com/kongdayan/magazine-library-android");
        link(body, getString(R.string.about_releases), "GitHub Releases", RELEASES_PAGE);
        link(body, getString(R.string.about_issues),
                "github.com/kongdayan/magazine-library-android/issues",
                "https://github.com/kongdayan/magazine-library-android/issues");
        link(body, getString(R.string.about_license), "MIT",
                "https://github.com/kongdayan/magazine-library-android/blob/main/LICENSE");
        link(body, getString(R.string.about_wristbeam), "github.com/kongdayan/wristbeam",
                "https://github.com/kongdayan/wristbeam");

        section(body, getString(R.string.about_check_updates));
        updateStatus = Ui.text(this, "", 12, Ui.MUTED);
        body.addView(updateStatus);
        Ui.space(body, 6);
        Button check = Ui.button(this, getString(R.string.about_check_updates), this::checkUpdates);
        check.setTag("aboutCheckUpdates");
        body.addView(check);

        section(body, getString(R.string.about_privacy_section));
        paragraph(body, getString(R.string.about_privacy));
        section(body, getString(R.string.about_disclaimer_section));
        paragraph(body, getString(R.string.about_disclaimer));
        section(body, getString(R.string.about_credits_section));
        paragraph(body, getString(R.string.about_credits));

        section(body, getString(R.string.about_languages));
        paragraph(body, getString(R.string.about_contribute));

        TextView footer = Ui.text(this, getString(R.string.about_made_by), 11, Ui.MUTED);
        footer.setGravity(Gravity.CENTER);
        Ui.pad(footer, 16, 24);
        body.addView(footer);
    }

    private void section(LinearLayout parent, String title) {
        Ui.space(parent, 14);
        parent.addView(Ui.heading(this, title, 13));
        Ui.space(parent, 6);
    }

    private void paragraph(LinearLayout parent, String text) {
        TextView view = Ui.text(this, text, 13, Ui.MUTED);
        view.setLineSpacing(Ui.dp(this, 4), 1f);
        parent.addView(view);
        Ui.space(parent, 4);
    }

    private void link(LinearLayout parent, String title, String detail, String url) {
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.bg(Ui.WHITE, Ui.dp(this, 10)));
        Ui.pad(row, 12, 10);
        row.setMinimumHeight(Ui.dp(this, 48));
        LinearLayout labels = Ui.col(this);
        labels.addView(Ui.heading(this, title, 14));
        if (detail != null && !detail.isEmpty()) {
            labels.addView(Ui.text(this, detail, 11, Ui.MUTED));
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(Ui.text(this, "›", 18, Ui.MUTED));
        row.setOnClickListener(v -> open(url));
        parent.addView(row);
        Ui.space(parent, 4);
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }

    private void checkUpdates() {
        if (checking) return;
        checking = true;
        updateStatus.setText(R.string.about_checking);
        updateStatus.setTypeface(Typeface.DEFAULT);
        new Thread(() -> {
            String tag = null, page = RELEASES_PAGE, error = null;
            try {
                HttpURLConnection connection =
                        (HttpURLConnection) new URL(RELEASES_API).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent",
                        "MagazineLibrary/" + BuildConfig.VERSION_NAME);
                try {
                    if (connection.getResponseCode() == 200) {
                        String body;
                        try (InputStream in = connection.getInputStream()) {
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            byte[] buffer = new byte[8192];
                            int n;
                            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                            body = out.toString("UTF-8");
                        }
                        JSONObject release = new JSONObject(body);
                        tag = release.optString("tag_name", null);
                        page = release.optString("html_url", RELEASES_PAGE);
                    } else {
                        error = "HTTP " + connection.getResponseCode();
                    }
                } finally {
                    connection.disconnect();
                }
            } catch (Exception e) {
                error = e.toString();
            }
            String latest = tag;
            String target = page;
            String failure = error;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                checking = false;
                if (latest == null || latest.isEmpty()) {
                    updateStatus.setText(R.string.about_update_failed);
                    return;
                }
                if (Versions.isNewer(latest, BuildConfig.VERSION_NAME)) {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.about_update_available, latest))
                            .setPositiveButton(R.string.about_open_release, (d, w) -> open(target))
                            .setNegativeButton(R.string.action_close, null)
                            .show();
                } else {
                    updateStatus.setText(getString(R.string.about_up_to_date,
                            BuildConfig.VERSION_NAME));
                }
            });
        }, "update-check").start();
    }

}

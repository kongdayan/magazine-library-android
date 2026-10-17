package com.magazinelibrary.app;

import android.app.AlertDialog;
import android.widget.ProgressBar;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Native presentation of the current browser response, fetched only on user navigation. */
final class NativeCatalog {
    final LinearLayout view;
    private final MainActivity host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private final CatalogImages images;
    private final LinearLayout body, controls, pager;
    private final ScrollView scroll;
    private final EditText search;
    private final TextView status;
    private final Button categoryButton, monthButton, orderButton;
    private final ArrayList<Option> categories = new ArrayList<>(), months = new ArrayList<>();
    private final LinkedHashMap<String, JSONObject> cache = new LinkedHashMap<>();

    private AlertDialog activeDialog;
    private JSONObject data;
    private String script, requested = "", listUrl = MainActivity.HOME,
            category = MainActivity.HOME, categoryTitle = "", month = "", term = "",
            sort = "latest", notice = "";
    private int generation;
    private boolean loading, closed, challengeActive;
    private Download pendingDownload;

    private static class Option {
        String title, url;

        Option(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    private static class Download {
        String url, title, source;

        Download(String url, String title, String source) {
            this.url = url;
            this.title = title;
            this.source = source;
        }
    }

    NativeCatalog(MainActivity activity, Bundle saved) {
        host = activity;
        prefs = host.getSharedPreferences("settings", 0);
        images = new CatalogImages(host.testWeb(), handler);
        view = Ui.col(host);
        view.setBackgroundColor(Ui.BG);
        Ui.pad(view, 12, 6);

        LinearLayout title = Ui.row(host);
        title.addView(Ui.heading(host, str(R.string.catalog_title), 20),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button website = Ui.button(host, str(R.string.catalog_web), () -> host.showBrowser(true));
        website.setTag("catalogBrowser");
        title.addView(website);
        view.addView(title);
        Ui.space(view, 6);

        controls = Ui.col(host);
        LinearLayout query = Ui.row(host);
        search = new EditText(host);
        search.setHint(R.string.catalog_search_hint);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setBackground(Ui.bg(Ui.WHITE, Ui.dp(host, 10)));
        Ui.pad(search, 10, 0);
        search.setMinHeight(Ui.dp(host, 44));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setTag("catalogSearch");
        query.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(host, 44), 1));
        Button submit = Ui.button(host, str(R.string.catalog_search), this::submit);
        submit.setTag("catalogSubmit");
        LinearLayout.LayoutParams submitLayout =
                new LinearLayout.LayoutParams(-2, Ui.dp(host, 44));
        submitLayout.setMargins(Ui.dp(host, 6), 0, 0, 0);
        query.addView(submit, submitLayout);
        controls.addView(query);
        search.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                submit();
                return true;
            }
            return false;
        });
        Ui.space(controls, 6);

        LinearLayout filters = Ui.row(host);
        categoryButton = chip(str(R.string.catalog_category, str(R.string.catalog_all)),
                () -> options(str(R.string.catalog_choose_category), categories, true));
        categoryButton.setTag("catalogCategory");
        monthButton = chip(str(R.string.catalog_month, str(R.string.catalog_month_any)),
                () -> options(str(R.string.catalog_choose_month), months, false));
        monthButton.setTag("catalogMonth");
        orderButton = chip(str(R.string.catalog_sort_latest), this::order);
        orderButton.setTag("catalogSort");
        filters.addView(categoryButton, new LinearLayout.LayoutParams(0, Ui.dp(host, 40), 1));
        LinearLayout.LayoutParams middle =
                new LinearLayout.LayoutParams(0, Ui.dp(host, 40), 1);
        middle.setMargins(Ui.dp(host, 5), 0, Ui.dp(host, 5), 0);
        filters.addView(monthButton, middle);
        filters.addView(orderButton, new LinearLayout.LayoutParams(Ui.dp(host, 78), Ui.dp(host, 40)));
        controls.addView(filters);
        view.addView(controls);

        status = Ui.text(host, str(R.string.catalog_loading), 12, Ui.MUTED);
        status.setTag("catalogStatus");
        Ui.pad(status, 0, 8);
        view.addView(status);
        scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        body = Ui.col(host);
        scroll.addView(body);
        view.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        pager = Ui.row(host);
        Ui.pad(pager, 0, 4);
        view.addView(pager);

        try (InputStream in = host.getAssets().open("catalog.js")) {
            script = readAll(in);
        } catch (IOException e) {
            script = "JSON.stringify({})";
        }
        listUrl = prefs.getString("catalogList", MainActivity.HOME);
        category = prefs.getString("catalogCategory", MainActivity.HOME);
        categoryTitle = prefs.getString("catalogCategoryTitle", "");
        month = prefs.getString("catalogMonth", "");
        term = prefs.getString("catalogTerm", "");
        sort = prefs.getString("catalogSort", "latest");
        if (saved != null) {
            listUrl = saved.getString("catalogList", MainActivity.HOME);
            category = saved.getString("catalogCategory", MainActivity.HOME);
            categoryTitle = saved.getString("catalogCategoryTitle", "");
            month = saved.getString("catalogMonth", "");
            term = saved.getString("catalogTerm", "");
            sort = saved.getString("catalogSort", "latest");
        }
        search.setText(term);
        updateChips();
    }

    private String str(int id, Object... args) {
        return host.getString(id, args);
    }

    private static String readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int size;
        while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
        return out.toString("UTF-8");
    }

    void start(Bundle saved) {
        String url = saved == null
                ? (prefs.getBoolean("restoreWebPage", true)
                ? prefs.getString("catalogUrl", MainActivity.HOME) : MainActivity.HOME)
                : saved.getString("catalogUrl", MainActivity.HOME);
        if (!CatalogRoutes.site(url) || Names.downloadUrl(url)) url = MainActivity.HOME;
        requested = url;
        if (saved != null && host.testWeb().getUrl() != null) {
            try {
                String snapshot = saved.getString("catalogData", "");
                if (!snapshot.isEmpty()) {
                    data = new JSONObject(snapshot);
                    updateOptions(data);
                    render();
                }
            } catch (Exception ignored) {
            }
            pageStarted(host.testWeb().getUrl());
            pageFinished(host.testWeb().getUrl());
        } else {
            load(url);
        }
    }

    private Button chip(CharSequence title, Runnable action) {
        Button b = Ui.button(host, title, action);
        b.setTextSize(12);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(Ui.dp(host, 6), 0, Ui.dp(host, 6), 0);
        return b;
    }

    private void submit() {
        term = search.getText().toString().trim();
        host.getSystemService(InputMethodManager.class)
                .hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
        pendingDownload = null;
        load(CatalogRoutes.query(term, category, month));
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = host.getSystemService(InputMethodManager.class);
        if (activeDialog != null && activeDialog.getWindow() != null) {
            keyboard.hideSoftInputFromWindow(
                    activeDialog.getWindow().getDecorView().getWindowToken(), 0);
        }
        keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
        search.clearFocus();
    }

    void load(String url) {
        if (!CatalogRoutes.site(url) || Names.downloadUrl(url)) return;
        pendingDownload = null;
        request(url);
    }

    private void request(String url) {
        notice = "";
        requested = url;
        loading = true;
        status.setText(R.string.catalog_loading);
        JSONObject cached = cache.get(url);
        if (cached != null) {
            data = cached;
            render();
        } else {
            body.removeAllViews();
            ProgressBar progress = new ProgressBar(host);
            body.addView(progress, new LinearLayout.LayoutParams(Ui.dp(host, 30), Ui.dp(host, 30)));
        }
        host.loadCatalogPage(url);
    }

    void pageStarted(String url) {
        images.reset();
        generation++;
        if (!CatalogRoutes.site(url) || Names.downloadUrl(url)) return;
        requested = url;
        loading = true;
        status.setText(R.string.catalog_loading);
        int token = generation;
        handler.postDelayed(() -> read(token, 0), 500);
    }

    void pageFinished(String url) {
        if (CatalogRoutes.site(url) && !Names.downloadUrl(url)) read(generation, 0);
    }

    private void read(int token, int attempt) {
        if (closed || token != generation) return;
        String source = host.testWeb().getUrl();
        if (!CatalogRoutes.site(source) || Names.downloadUrl(source)) return;
        host.testWeb().evaluateJavascript(script, raw -> {
            if (closed || token != generation || !Objects.equals(source, host.testWeb().getUrl())) return;
            try {
                Object value = new JSONTokener(raw).nextValue();
                JSONObject page = new JSONObject((String) value);
                if (page.optBoolean("challenge")) {
                    challenge();
                    return;
                }
                JSONArray items = page.optJSONArray("items");
                boolean detail = page.optBoolean("detail");
                boolean recognized = detail || (items != null && items.length() > 0) || page.optBoolean("empty");
                if (!recognized) {
                    if (attempt < 70) {
                        handler.postDelayed(() -> read(token, attempt + 1), 500);
                        return;
                    }
                    error(str(R.string.catalog_unsupported));
                    return;
                }
                loading = false;
                data = page;
                updateOptions(page);
                requested = page.optString("url", source);
                prefs.edit().putString("catalogUrl", requested).apply();
                if (!detail) {
                    listUrl = requested;
                    restoreFilters(requested);
                }
                cache.put(requested, page);
                while (cache.size() > 8) cache.remove(cache.keySet().iterator().next());
                render();
                if (challengeActive) {
                    challengeActive = false;
                    host.showVerificationBrowser(false);
                }
                Download download = pendingDownload;
                if (download != null && Objects.equals(download.source, requested)) {
                    pendingDownload = null;
                    boolean exists = false;
                    JSONArray links = page.optJSONArray("pdfs");
                    if (links != null) {
                        for (int n = 0; n < links.length(); n++) {
                            if (download.url.equals(links.getJSONObject(n).optString("url"))) exists = true;
                        }
                    }
                    if (exists) host.prepareNativeDownload(download.url, download.title, download.source);
                    else error(str(R.string.catalog_pdf_changed));
                }
            } catch (Exception e) {
                if (attempt < 20) handler.postDelayed(() -> read(token, attempt + 1), 500);
                else error(str(R.string.catalog_load_failed));
            }
        });
    }

    void challenge() {
        challengeActive = true;
        loading = false;
        notice = str(R.string.catalog_challenge);
        status.setText(notice);
        host.showVerificationBrowser(true);
    }

    void error(String message) {
        loading = false;
        notice = message;
        status.setText(message);
        body.removeAllViews();
        body.addView(Ui.text(host, message, 14, Ui.MUTED));
        Ui.space(body, 10);
        LinearLayout row = Ui.row(host);
        row.addView(Ui.button(host, str(R.string.action_retry),
                () -> load(requested.isEmpty() ? listUrl : requested)));
        row.addView(Ui.button(host, str(R.string.action_open_web), () -> host.showBrowser(true)));
        body.addView(row);
    }

    void downloadStatus(String message) {
        notice = message;
        status.setText(message);
    }

    void sync() {
        pageFinished(host.testWeb().getUrl());
    }

    private void updateOptions(JSONObject page) throws JSONException {
        merge(categories, page.optJSONArray("options"));
        merge(months, page.optJSONArray("months"));
    }

    private void restoreFilters(String url) {
        try {
            URI uri = URI.create(url);
            Map<String, String> params = new HashMap<>();
            if (uri.getRawQuery() != null) {
                for (String pair : uri.getRawQuery().split("&")) {
                    String[] parts = pair.split("=", 2);
                    params.put(parts[0], parts.length == 2
                            ? URLDecoder.decode(parts[1], "UTF-8") : "");
                }
            }
            term = params.getOrDefault("s", "");
            if (!search.hasFocus()) search.setText(term);
            month = "";
            if (params.getOrDefault("year", "").matches("\\d{4}")
                    && params.getOrDefault("monthnum", "").matches("\\d{1,2}")) {
                month = params.get("year") + "/"
                        + String.format(Locale.ROOT, "%02d", Integer.parseInt(params.get("monthnum")));
            }
            String path = uri.getPath().replaceFirst("/page/\\d+/?$", "/");
            if (path.matches("/\\d{4}/\\d{2}/")) month = path.substring(1, path.length() - 1);
            category = MainActivity.HOME;
            categoryTitle = "";
            String slug = params.getOrDefault("category_name", "");
            for (Option option : categories) {
                String optionPath = URI.create(option.url).getPath();
                if (!slug.isEmpty()
                        ? optionPath.replaceAll("/+$", "").endsWith("/" + slug)
                        : optionPath.equals(path)) {
                    category = option.url;
                    categoryTitle = option.title;
                    break;
                }
            }
            updateChips();
            prefs.edit()
                    .putString("catalogList", listUrl)
                    .putString("catalogCategory", category)
                    .putString("catalogCategoryTitle", categoryTitle)
                    .putString("catalogMonth", month)
                    .putString("catalogTerm", term)
                    .putString("catalogSort", sort)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private void merge(ArrayList<Option> target, JSONArray incoming) throws JSONException {
        if (incoming == null) return;
        for (int n = 0; n < incoming.length(); n++) {
            JSONObject option = incoming.getJSONObject(n);
            String url = option.optString("url"), title = option.optString("title");
            if (CatalogRoutes.site(url) && !title.trim().isEmpty()
                    && target.stream().noneMatch(x -> x.url.equals(url))) {
                target.add(new Option(title.substring(0, Math.min(80, title.length())), url));
            }
        }
    }

    private void updateChips() {
        String name = category.equals(MainActivity.HOME) || categoryTitle.isEmpty()
                ? str(R.string.catalog_all) : categoryTitle;
        categoryButton.setText(str(R.string.catalog_chip_value,
                str(R.string.catalog_category, name)));
        monthButton.setText(str(R.string.catalog_chip_value, month.isEmpty()
                ? str(R.string.catalog_month, str(R.string.catalog_month_any)) : month));
        orderButton.setText(str(R.string.catalog_chip_value, sortLabel()));
    }

    private String sortLabel() {
        if (sort.equals("title")) return str(R.string.catalog_sort_title);
        if (sort.equals("oldest")) return str(R.string.catalog_sort_oldest);
        return str(R.string.catalog_sort_latest);
    }

    private void options(String title, ArrayList<Option> values, boolean isCategory) {
        if (values.isEmpty() && isCategory) {
            Toast.makeText(host, R.string.catalog_wait_page, Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout panel = Ui.col(host);
        Ui.pad(panel, 16, 8);
        EditText filter = new EditText(host);
        filter.setTag(isCategory ? "catalogCategorySearch" : "catalogMonthSearch");
        filter.setSingleLine(true);
        filter.setHint(str(isCategory
                ? R.string.catalog_filter_categories : R.string.catalog_filter_months));
        panel.addView(filter);
        ListView list = new ListView(host);
        panel.addView(list, new LinearLayout.LayoutParams(-1, Ui.dp(host, 360)));
        ArrayList<Option> visible = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(host, android.R.layout.simple_list_item_1, labels);
        list.setAdapter(adapter);
        Runnable refresh = () -> {
            visible.clear();
            labels.clear();
            visible.add(new Option(str(isCategory
                    ? R.string.catalog_category_all : R.string.catalog_month_any),
                    isCategory ? MainActivity.HOME : ""));
            if (!isCategory) visible.add(new Option(str(R.string.catalog_manual_month), "manual-month"));
            String query = filter.getText().toString().toLowerCase(Locale.ROOT);
            for (Option value : values) {
                if (value.title.toLowerCase(Locale.ROOT).contains(query)) visible.add(value);
            }
            for (Option value : visible) labels.add(value.title);
            adapter.notifyDataSetChanged();
        };
        refresh.run();
        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle(title).setView(panel)
                .setNegativeButton(R.string.action_close, null).create();
        filter.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh.run();
            }

            public void afterTextChanged(Editable e) {
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            Option selected = visible.get(position);
            if (selected.url.equals("manual-month")) {
                dialog.dismiss();
                chooseMonth();
                return;
            }
            if (isCategory) {
                category = selected.url;
                categoryTitle = selected.title;
            } else {
                month = selected.url.isEmpty() ? ""
                        : URI.create(selected.url).getPath().replaceAll("^/|/$", "");
            }
            updateChips();
            hideKeyboard();
            dialog.dismiss();
            term = search.getText().toString().trim();
            load(CatalogRoutes.query(term, category, month));
        });
        activeDialog = dialog;
        dialog.show();
    }

    private void order() {
        String[] labels = {
                str(R.string.catalog_sort_latest),
                str(R.string.catalog_sort_oldest),
                str(R.string.catalog_sort_title)};
        activeDialog = new AlertDialog.Builder(host)
                .setTitle(R.string.catalog_sort_this_page)
                .setItems(labels, (dialog, which) -> {
                    sort = new String[]{"latest", "oldest", "title"}[which];
                    updateChips();
                    prefs.edit().putString("catalogSort", sort).apply();
                    render();
                }).show();
    }

    private void chooseMonth() {
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setHint(R.string.catalog_month_hint);
        input.setText(month);
        activeDialog = new AlertDialog.Builder(host)
                .setTitle(R.string.catalog_month_title)
                .setView(input)
                .setPositiveButton(R.string.catalog_apply, (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.matches("(?:19|20)\\d{2}/(?:0[1-9]|1[0-2])")) {
                        Toast.makeText(host, R.string.catalog_month_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    hideKeyboard();
                    month = value;
                    term = search.getText().toString().trim();
                    updateChips();
                    load(CatalogRoutes.query(term, category, month));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void render() {
        if (data == null) return;
        body.removeAllViews();
        boolean detail = data.optBoolean("detail");
        controls.setVisibility(detail ? View.GONE : View.VISIBLE);
        pager.setVisibility(detail ? View.GONE : View.VISIBLE);
        try {
            if (detail) renderDetail();
            else renderList();
        } catch (JSONException e) {
            error(str(R.string.catalog_load_failed));
        }
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void renderList() throws JSONException {
        JSONArray items = data.optJSONArray("items");
        ArrayList<JSONObject> entries = new ArrayList<>();
        if (items != null) {
            for (int n = 0; n < items.length(); n++) {
                JSONObject item = items.getJSONObject(n);
                if (CatalogRoutes.site(item.optString("url"))
                        && !item.optString("title").isEmpty()) {
                    entries.add(item);
                }
            }
        }
        if (sort.equals("title")) {
            entries.sort(Comparator.comparing(o -> o.optString("title"), String.CASE_INSENSITIVE_ORDER));
        } else if (sort.equals("oldest")) {
            entries.sort(Comparator.comparing(o -> o.optString("date")));
        }
        int page = data.optInt("page", CatalogRoutes.number(requested));
        String pageText = str(R.string.catalog_page_status, page, entries.size());
        status.setText(loading ? str(R.string.catalog_loading)
                : (notice.isEmpty() ? pageText : notice));
        if (entries.isEmpty()) {
            Ui.space(body, 20);
            body.addView(Ui.heading(host, str(R.string.catalog_empty_title), 18));
            Ui.space(body, 8);
            body.addView(Ui.text(host, str(R.string.catalog_empty_desc), 13, Ui.MUTED));
        }
        for (int n = 0; n < entries.size(); n += 2) {
            LinearLayout row = Ui.row(host);
            row.setGravity(Gravity.TOP);
            for (int col = 0; col < 2; col++) {
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -2, 1);
                cell.setMargins(0, 0, col == 0 ? Ui.dp(host, 8) : 0, 0);
                if (n + col < entries.size()) row.addView(card(entries.get(n + col)), cell);
                else row.addView(new View(host), cell);
            }
            body.addView(row);
            Ui.space(body, 8);
        }
        LinearLayout nav = Ui.row(host);
        String current = data.optString("url"), prev = data.optString("previous"),
                next = data.optString("next");
        if (prev.isEmpty() && page > 1) prev = CatalogRoutes.page(current, page - 1);
        String previousUrl = prev;
        Button previous = chip(str(R.string.catalog_previous), () -> load(previousUrl));
        previous.setTag("catalogPrevious");
        previous.setEnabled(CatalogRoutes.site(prev));
        previous.setAlpha(previous.isEnabled() ? 1 : .4f);
        nav.addView(previous, new LinearLayout.LayoutParams(0, Ui.dp(host, 44), 1));
        Button number = chip(str(R.string.catalog_page_number, page), this::jump);
        number.setTag("catalogPage");
        nav.addView(number, new LinearLayout.LayoutParams(0, Ui.dp(host, 44), 1));
        Button following = chip(str(R.string.catalog_next), () -> load(next));
        following.setTag("catalogNext");
        following.setEnabled(CatalogRoutes.site(next));
        following.setAlpha(following.isEnabled() ? 1 : .4f);
        nav.addView(following, new LinearLayout.LayoutParams(0, Ui.dp(host, 44), 1));
        pager.removeAllViews();
        pager.addView(nav, new LinearLayout.LayoutParams(-1, Ui.dp(host, 44)));
        Ui.space(body, 8);
    }

    private LinearLayout card(JSONObject item) {
        String url = item.optString("url"), title = item.optString("title");
        LinearLayout card = Ui.col(host);
        card.setTag("catalogItem:" + url);
        card.setContentDescription(title);
        card.setBackground(Ui.bg(Ui.WHITE, Ui.dp(host, 12)));
        Ui.pad(card, 8, 8);
        ImageView cover = new ImageView(host);
        cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cover.setContentDescription(str(R.string.catalog_cover_desc, title));
        images.load(item.optString("image"), cover);
        card.addView(cover, new LinearLayout.LayoutParams(-1, Ui.dp(host, 154)));
        String series = Names.series(title);
        TextView name = Ui.heading(host, series, 13);
        name.setLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(name);
        String issue = title.startsWith(series)
                ? title.substring(series.length()).replaceFirst("^[\\s–—:-]+", "").trim() : "";
        TextView issueText = Ui.text(host, issue, 11, Ui.GREEN);
        issueText.setLines(1);
        issueText.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(issueText);
        TextView tags = Ui.text(host, categoryText(item.optJSONArray("categories")), 10, Ui.MUTED);
        tags.setSingleLine(true);
        tags.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(tags);
        card.setOnClickListener(v -> load(url));
        return card;
    }

    private String categoryText(JSONArray values) {
        ArrayList<String> tags = new ArrayList<>();
        if (values != null) {
            for (int n = 0; n < Math.min(3, values.length()); n++) tags.add(values.optString(n));
        }
        return String.join(" · ", tags);
    }

    private void renderDetail() throws JSONException {
        String title = data.optString("heading"), source = data.optString("url");
        status.setText(notice.isEmpty() ? str(R.string.catalog_detail) : notice);
        Button back = chip(str(R.string.catalog_back_results), () -> load(listUrl));
        back.setTag("catalogBack");
        body.addView(back);
        Ui.space(body, 8);

        LinearLayout summary = Ui.row(host);
        summary.setGravity(Gravity.TOP);
        ImageView cover = new ImageView(host);
        cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        images.load(data.optString("image"), cover);
        summary.addView(cover, new LinearLayout.LayoutParams(Ui.dp(host, 105), Ui.dp(host, 146)));
        LinearLayout info = Ui.col(host);
        Ui.pad(info, 10, 0);
        TextView name = Ui.heading(host, title, 18);
        name.setTag("catalogDetailTitle");
        info.addView(name);
        Ui.space(info, 8);
        info.addView(Ui.text(host, categoryText(data.optJSONArray("categories")), 12, Ui.MUTED));
        String date = data.optString("date");
        if (date.length() >= 10) {
            info.addView(Ui.text(host, str(R.string.catalog_published, date.substring(0, 10)),
                    11, Ui.MUTED));
        }
        summary.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(summary);
        Ui.space(body, 14);

        String desc = data.optString("description");
        if (!desc.isEmpty() && !Names.title(desc).equals(Names.title(title))) {
            TextView description = Ui.text(host, desc, 13, Ui.MUTED);
            description.setMaxLines(8);
            description.setEllipsize(TextUtils.TruncateAt.END);
            body.addView(description);
            Ui.space(body, 12);
        }
        body.addView(Ui.heading(host, str(R.string.catalog_pdfs), 15));
        Ui.space(body, 6);
        JSONArray pdfs = data.optJSONArray("pdfs");
        int count = 0;
        if (pdfs != null) {
            for (int n = 0; n < pdfs.length(); n++) {
                JSONObject link = pdfs.getJSONObject(n);
                String url = link.optString("url");
                if (!Names.downloadUrl(url)) continue;
                String filename = Names.clickedTitle(link.optString("title"), url, title);
                Button download = Ui.button(host,
                        str(R.string.catalog_download, filename),
                        () -> download(url, filename, source));
                download.setTag("catalogPdf:" + url);
                download.setTextSize(13);
                download.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                download.setMaxLines(2);
                download.setEllipsize(TextUtils.TruncateAt.END);
                body.addView(download, new LinearLayout.LayoutParams(-1, Ui.dp(host, 56)));
                Ui.space(body, 6);
                count++;
            }
        }
        if (count == 0) {
            body.addView(Ui.text(host, str(R.string.catalog_no_pdfs), 13, Ui.MUTED));
            body.addView(Ui.button(host, str(R.string.action_open_web), () -> host.showBrowser(true)));
        }
        Button similar = Ui.button(host, str(R.string.catalog_related_search), () -> {
            term = Names.series(title);
            category = MainActivity.HOME;
            categoryTitle = "";
            month = "";
            search.setText(term);
            updateChips();
            load(CatalogRoutes.query(term, category, month));
        });
        similar.setTag("catalogRelatedSearch");
        body.addView(similar);
        Ui.space(body, 14);
        JSONArray related = data.optJSONArray("items");
        if (related != null && related.length() > 0) {
            body.addView(Ui.heading(host, str(R.string.catalog_related), 15));
            Ui.space(body, 6);
            for (int n = 0; n < Math.min(6, related.length()); n++) {
                JSONObject item = related.getJSONObject(n);
                if (!CatalogRoutes.site(item.optString("url"))) continue;
                Button link = Ui.button(host, item.optString("title"),
                        () -> load(item.optString("url")));
                link.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                link.setTextSize(12);
                link.setMaxLines(2);
                body.addView(link, new LinearLayout.LayoutParams(-1, Ui.dp(host, 48)));
                Ui.space(body, 4);
            }
        }
    }

    private void download(String url, String title, String source) {
        notice = str(R.string.catalog_preparing);
        status.setText(notice);
        if (source.equals(host.testWeb().getUrl())) {
            host.prepareNativeDownload(url, title, source);
        } else {
            pendingDownload = new Download(url, title, source);
            request(source);
        }
    }

    private void jump() {
        if (data == null) return;
        EditText input = new EditText(host);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setTag("catalogJumpInput");
        input.setHint(R.string.catalog_page_hint);
        input.setText(String.valueOf(data.optInt("page", 1)));
        activeDialog = new AlertDialog.Builder(host)
                .setTitle(R.string.catalog_page_jump)
                .setView(input)
                .setPositiveButton(R.string.catalog_go, (dialog, which) -> {
                    try {
                        hideKeyboard();
                        load(CatalogRoutes.page(data.optString("url"),
                                Integer.parseInt(input.getText().toString())));
                    } catch (Exception e) {
                        Toast.makeText(host, R.string.catalog_page_invalid, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    boolean back() {
        if (data != null && data.optBoolean("detail")) {
            load(listUrl);
            return true;
        }
        return false;
    }

    void save(Bundle out) {
        if (data != null) out.putString("catalogData", data.toString());
        out.putString("catalogUrl", requested);
        out.putString("catalogList", listUrl);
        out.putString("catalogCategory", category);
        out.putString("catalogCategoryTitle", categoryTitle);
        out.putString("catalogMonth", month);
        out.putString("catalogTerm", term);
        out.putString("catalogSort", sort);
    }

    JSONObject snapshot() {
        return data;
    }

    AlertDialog dialog() {
        return activeDialog != null && activeDialog.isShowing() ? activeDialog : null;
    }

    void close() {
        if (activeDialog != null) activeDialog.dismiss();
        closed = true;
        generation++;
        handler.removeCallbacksAndMessages(null);
        images.close();
    }
}

package com.foxlauncher.home;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Fox Launcher home screen for a 1 GB Android 7.1 TV box.
 *
 * Top to bottom: clock, date and shortcuts; the name and usage of whatever has focus; the
 * editable home row of up to five big tiles; a small grid with every other app, ordered by
 * how often it is opened; and one line of key hints that follows the focus.
 *
 * Holding OK for half a second on an app opens its menu. The pencil next to the home row title enters edit
 * mode, where OK adds, grabs and drops tiles and Back leaves. Everything is built in code with
 * platform widgets only, and the window is software rendered, to keep the process small.
 */
public final class MainActivity extends Activity {

    private static final int MAX_PIN = 5;
    private static final int COLUMNS = 5;
    private static final int NEUTRAL_BG = 0xFF17181D;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_SOFT = 0xB3FFFFFF;
    private static final int AMBER = 0xFFFFC107;
    private static final int RED = 0xFFFF8A80;
    /** Free memory, in MB, below which the figure turns amber and then red. */
    private static final int RAM_LOW_MB = 20;
    private static final int RAM_CRITICAL_MB = 10;
    /** How long the opening notice may stay if the app never takes the screen. */
    private static final int OPENING_TIMEOUT_MS = 15_414;
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private static final String PREF_PINS = "pins";
    private static final String PREF_HIDDEN = "hidden";
    private static final String PREF_USES = "u_";
    private static final String PREF_HIDE_DEFAULTS = "hideDefaults2";
    /**
     * System screens that only add noise; hidden once, recoverable from the hidden-apps tile.
     * Settings duplicates the device-settings button; the Downloads entry is DocumentsUI's launcher
     * entry on this firmware.
     */
    private static final String[] HIDDEN_BY_DEFAULT = {
            "com.android.tv.settings", "com.android.settings",
            "com.android.providers.downloads.ui", "com.android.documentsui"};

    private static final String SEP = "  ·  ";
    private static final String[] DAYS_ES =
            {"domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"};
    private static final String[] MONTHS_ES =
            {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
                    "septiembre", "octubre", "noviembre", "diciembre"};
    private static final String[] DAYS_EN =
            {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    private static final String[] MONTHS_EN =
            {"January", "February", "March", "April", "May", "June", "July", "August",
                    "September", "October", "November", "December"};

    private static String guideHome() {
        return S.t("OK: abrir", "OK: open") + SEP + S.t("Mantené OK: opciones", "Hold OK: options")
                + SEP + S.t("Lápiz: editar inicio", "Pencil: edit home");
    }

    private static String guideExit() {
        return S.t("Atrás: salir de edición", "Back: leave editing");
    }

    /** One design unit in pixels: the layout is drawn for a 720-line screen and scaled. */
    float u;

    private SharedPreferences prefs;
    private final ArrayList<App> apps = new ArrayList<>();
    private final ArrayList<String> pins = new ArrayList<>();
    private final HashSet<String> hidden = new HashSet<>();
    /** Every tile on screen, pinned row first; rebuilt by {@link #refresh}. */
    private final ArrayList<TileView> tiles = new ArrayList<>();
    /** Apps of the small grid in the order shown. */
    private final ArrayList<App> gridOrder = new ArrayList<>();

    private LinearLayout pinRow;
    private LinearLayout grid;
    private ScrollView scroll;
    private TextView clock;
    private TextView date;
    private TextView network;
    private TextView ram;
    private TextView heroTitle;
    private TextView heroSub;
    private TextView inicioLabel;
    private PencilView pencil;
    private TextView guide;
    private final ColorDrawable background = new ColorDrawable(NEUTRAL_BG);

    private final Handler handler = new Handler();
    private final Calendar calendar = Calendar.getInstance();
    private final StringBuilder clockText = new StringBuilder(8);
    private int lastDay = -1;
    private int ticks;

    private String focusPkg;
    private TileView focusTile;
    private String chromeGuide = "";

    // Edit mode. "grabbed" is the pinned app being moved; "replacing" is the app waiting for the
    // user to pick which pinned one it takes the place of.
    private boolean editing;
    private String grabbed;
    private App replacing;

    private TileView openingTile;
    /** How often the open app's session is written down, so a power cut loses at most this. */
    private static final long HEARTBEAT_MS = 60_000L;

    /** Runs while an app opened from here is on screen; the launcher's process stays alive. */
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            Usage.beat(prefs, System.currentTimeMillis());
            handler.postDelayed(this, HEARTBEAT_MS);
        }
    };
    private boolean syncing;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            updateClock(now);
            if (ticks++ % 10 == 0) {
                updateStatus();
            }
            handler.postDelayed(this, 1000 - now % 1000);
        }
    };

    private final Runnable clearOpening = new Runnable() {
        @Override
        public void run() {
            handler.removeCallbacks(this);
            if (openingTile != null) {
                openingTile.opening = false;
                openingTile.invalidate();
                openingTile = null;
            }
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("fox", MODE_PRIVATE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        u = Math.min(dm.widthPixels, dm.heightPixels) / 720f;
        getWindow().setBackgroundDrawable(background);

        split(prefs.getString(PREF_PINS, ""), pins);
        ArrayList<String> hiddenList = new ArrayList<>();
        split(prefs.getString(PREF_HIDDEN, ""), hiddenList);
        hidden.addAll(hiddenList);

        buildUi();
        loadApps();
        refresh();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        clearOpening.run();
        // Back on the home screen: whatever app was open is closed now. This also settles a
        // session that was cut short when the box lost power with the app still open.
        handler.removeCallbacks(heartbeat);
        Usage.endSession(prefs, System.currentTimeMillis(), SystemClock.elapsedRealtime());
        syncUsage();
        refreshHero();
        ticks = 0;
        handler.removeCallbacks(tick);
        tick.run();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    /** Home pressed while already on the home screen: leave any mode and go to the first tile. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            replacing = null;
            grabbed = null;
            if (editing) {
                setEditing(false);
            }
            focusPkg = null;
            scroll.scrollTo(0, 0);
            redrawPins();
            restoreFocus();
        }
    }

    /** Back never leaves the home screen; it steps out of whatever mode is active. */
    @Override
    public void onBackPressed() {
        if (replacing != null) {
            focusPkg = replacing.pkg;
            replacing = null;
            redrawPins();
            restoreFocus();
            updateGuide();
        } else if (grabbed != null) {
            grabbed = null;
            redrawPins();
            updateGuide();
        } else if (editing) {
            setEditing(false);
        }
    }

    // ---------------------------------------------------------------- layout

    private int px(float units) {
        return Math.round(units * u);
    }

    private TextView text(float sizeUnits, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeUnits * u);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private LinearLayout box(int orientation) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(orientation);
        return l;
    }

    /** Rounded background shared by the top-bar buttons and the menu rows. */
    private GradientDrawable pill(boolean focused, int restColor) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(8 * u);
        d.setColor(focused ? 0x38FFFFFF : restColor);
        d.setStroke(px(3), focused ? 0xFFFFFFFF : 0x00FFFFFF);
        return d;
    }

    private TextView button(final String label, final String purpose, View.OnClickListener onClick) {
        final TextView b = text(18, TEXT);
        b.setText(label);
        b.setPadding(px(14), px(8), px(14), px(8));
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(pill(false, 0x24FFFFFF));
        b.setOnClickListener(onClick);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                b.setBackground(pill(hasFocus, 0x24FFFFFF));
                if (hasFocus) {
                    chromeFocus(label, purpose,
                            "OK: " + Character.toLowerCase(purpose.charAt(0)) + purpose.substring(1));
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = px(8);
        b.setLayoutParams(lp);
        return b;
    }

    private void buildUi() {
        LinearLayout root = box(LinearLayout.VERTICAL);
        root.setPadding(px(64), px(26), px(64), px(14));

        // Clock and date on the left, status and shortcuts on the right.
        LinearLayout top = box(LinearLayout.HORIZONTAL);
        LinearLayout left = box(LinearLayout.VERTICAL);
        clock = text(54, TEXT);
        clock.setTypeface(MEDIUM);
        date = text(26, 0xE6FFFFFF);
        date.setPadding(0, px(4), 0, 0);
        left.addView(clock);
        left.addView(date);
        top.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout right = box(LinearLayout.VERTICAL);
        LinearLayout statusRow = box(LinearLayout.HORIZONTAL);
        statusRow.setPadding(px(8), px(4), 0, px(12));
        network = text(18, TEXT_SOFT);
        ram = text(18, TEXT_SOFT);
        statusRow.addView(network);
        statusRow.addView(ram);
        LinearLayout buttons = box(LinearLayout.HORIZONTAL);
        buttons.addView(button("Wi-Fi", S.t("Abrir la configuración de redes Wi-Fi", "Open the Wi-Fi network settings"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        open(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    }
                }));
        buttons.addView(button(S.t("Liberar memoria", "Free memory"), S.t("Cerrar las apps que quedaron en segundo plano", "Close the apps left running in the background"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        freeMemory();
                    }
                }));
        buttons.addView(button(S.t("Ajustes del equipo", "Device settings"), S.t("Abrir los ajustes de Android del equipo", "Open the Android settings of this device"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        open(new Intent(Settings.ACTION_SETTINGS));
                    }
                }));
        right.addView(statusRow);
        right.addView(buttons);
        top.addView(right);
        root.addView(top);

        // Name and usage of whatever has focus.
        LinearLayout hero = box(LinearLayout.VERTICAL);
        hero.setPadding(px(32), px(8), px(32), px(10));
        GradientDrawable heroFrame = new GradientDrawable();
        heroFrame.setCornerRadius(12 * u);
        heroFrame.setColor(0x24FFFFFF);
        heroFrame.setStroke(px(2), 0x66FFFFFF);
        hero.setBackground(heroFrame);
        heroTitle = text(34, TEXT);
        heroTitle.setTypeface(MEDIUM);
        heroTitle.setGravity(Gravity.CENTER);
        heroSub = text(20, 0xCCFFFFFF);
        heroSub.setGravity(Gravity.CENTER);
        heroSub.setPadding(0, px(3), 0, 0);
        hero.addView(heroTitle);
        hero.addView(heroSub);
        // A fixed width: the frame does not jump as the focus moves, and the usage line fits.
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                px(820), LinearLayout.LayoutParams.WRAP_CONTENT);
        heroParams.gravity = Gravity.CENTER_HORIZONTAL;
        heroParams.topMargin = px(6);
        root.addView(hero, heroParams);

        LinearLayout header = box(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(px(6), px(10), 0, px(6));
        inicioLabel = text(18, TEXT_SOFT);
        inicioLabel.setText(S.t("Inicio", "Home"));
        pencil = new PencilView(this);
        LinearLayout.LayoutParams pencilParams = new LinearLayout.LayoutParams(px(36), px(28));
        pencilParams.leftMargin = px(12);
        header.addView(inicioLabel);
        header.addView(pencil, pencilParams);
        root.addView(header);

        pinRow = box(LinearLayout.HORIZONTAL);
        root.addView(pinRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(196)));

        TextView allLabel = text(18, TEXT_SOFT);
        allLabel.setText(S.t("Todas las apps", "All apps"));
        allLabel.setPadding(px(6), px(12), 0, px(6));
        root.addView(allLabel);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        grid = box(LinearLayout.VERTICAL);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        guide = text(18, TEXT_SOFT);
        guide.setPadding(px(6), px(8), 0, 0);
        root.addView(guide);

        setContentView(root);
    }

    // ------------------------------------------------------------------ apps

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        HashSet<String> seen = new HashSet<>();
        int iconSize = px(44);
        String self = getPackageName();
        // TV entry points first, so a TV app is opened through its leanback activity.
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String category : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
                ActivityInfo ai = ri.activityInfo;
                if (ai == null || self.equals(ai.packageName) || !seen.add(ai.packageName)) {
                    continue;
                }
                App a = new App();
                a.pkg = ai.packageName;
                a.component = new ComponentName(ai.packageName, ai.name);
                CharSequence label = ri.loadLabel(pm);
                a.label = TextUtils.isEmpty(label) ? ai.packageName : label.toString();
                a.system = (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                a.icon = render(ri.loadIcon(pm), iconSize, iconSize);
                a.color = dominantColor(a.icon);
                a.uses = prefs.getInt(PREF_USES + a.pkg, 0);
                a.sortUses = a.uses;
                apps.add(a);
            }
        }

        if (!prefs.contains(PREF_PINS)) {
            // First run: start with the two apps this box is used for, when present.
            for (String pkg : new String[]{"com.google.android.youtube.tv", "com.android.vending"}) {
                if (find(pkg) != null) {
                    pins.add(pkg);
                }
            }
            savePins();
        }
        if (!prefs.getBoolean(PREF_HIDE_DEFAULTS, false)) {
            for (String pkg : HIDDEN_BY_DEFAULT) {
                if (find(pkg) != null) {
                    hidden.add(pkg);
                }
            }
            saveHidden();
            prefs.edit().putBoolean(PREF_HIDE_DEFAULTS, true).apply();
        }

        // Forget apps that were uninstalled; a hidden app is never pinned.
        ArrayList<String> gone = new ArrayList<>();
        for (String pkg : hidden) {
            if (find(pkg) == null) {
                gone.add(pkg);
            }
        }
        hidden.removeAll(gone);
        for (int i = pins.size() - 1; i >= 0; i--) {
            if (find(pins.get(i)) == null || hidden.contains(pins.get(i))) {
                pins.remove(i);
            }
        }
        if (grabbed != null && !pins.contains(grabbed)) {
            grabbed = null;
        }
        if (replacing != null) {
            replacing = find(replacing.pkg);
        }
    }

    private App find(String pkg) {
        for (App a : apps) {
            if (a.pkg.equals(pkg)) {
                return a;
            }
        }
        return null;
    }

    private static Bitmap render(Drawable d, int w, int h) {
        if (d == null || w <= 0 || h <= 0) {
            return null;
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return b;
    }

    /**
     * Loads the large art of a pinned app. Normal tiles use the TV banner when the app has one,
     * else a bigger icon. Stretched tiles always use the icon: they print the name beside the
     * art, and a banner already carries the name, so the two would repeat each other.
     */
    private void ensureBig(App a, boolean wide) {
        if (a.big != null && a.bigWide == wide) {
            return;
        }
        a.big = null;
        a.bigWide = wide;
        PackageManager pm = getPackageManager();
        try {
            ActivityInfo ai = pm.getActivityInfo(a.component, 0);
            Drawable banner = wide ? null : ai.loadBanner(pm);
            if (banner != null) {
                a.big = render(banner, px(320), px(180));
                a.bigIsBanner = true;
                int edge = a.big == null ? 0 : a.big.getPixel(2, 2);
                a.cardColor = (edge >>> 24) >= 250 ? edge | 0xFF000000 : 0;
            } else {
                int size = px(wide ? 144 : 112);
                a.big = render(largeIcon(pm, ai), size, size);
                a.bigIsBanner = false;
                a.cardColor = 0;
            }
        } catch (PackageManager.NameNotFoundException e) {
            a.big = null;
        }
    }

    /** The icon from a high-density bucket, so it stays sharp when drawn large. */
    private static Drawable largeIcon(PackageManager pm, ActivityInfo ai) {
        int id = ai.getIconResource();
        if (id != 0) {
            try {
                Drawable d = pm.getResourcesForApplication(ai.applicationInfo)
                        .getDrawableForDensity(id, DisplayMetrics.DENSITY_XXHIGH);
                if (d != null) {
                    return d;
                }
            } catch (PackageManager.NameNotFoundException | RuntimeException e) {
                // Fall back to the default-density icon below.
            }
        }
        return ai.loadIcon(pm);
    }

    /**
     * Dominant colour of the icon: the average of the pixels in its strongest hue band. Averaging
     * the whole icon turns multi-colour logos into mud, so only the winning band is averaged.
     */
    private static int dominantColor(Bitmap b) {
        final int fallback = 0xFF5D6A7A;
        if (b == null) {
            return fallback;
        }
        int w = b.getWidth();
        int h = b.getHeight();
        int[] pixels = new int[w * h];
        b.getPixels(pixels, 0, w, 0, 0, w, h);
        final int bands = 12;
        double[] weight = new double[bands];
        double[] r = new double[bands];
        double[] g = new double[bands];
        double[] bl = new double[bands];
        float[] hsv = new float[3];
        for (int c : pixels) {
            if ((c >>> 24) < 128) {
                continue;
            }
            Color.colorToHSV(c, hsv);
            // Greys and near-blacks carry no hue worth showing.
            if (hsv[1] < 0.25f || hsv[2] < 0.25f) {
                continue;
            }
            int band = Math.min(bands - 1, (int) (hsv[0] / 360f * bands));
            double wgt = hsv[1] * hsv[2];
            weight[band] += wgt;
            r[band] += wgt * ((c >> 16) & 0xFF);
            g[band] += wgt * ((c >> 8) & 0xFF);
            bl[band] += wgt * (c & 0xFF);
        }
        int best = 0;
        for (int i = 1; i < bands; i++) {
            if (weight[i] > weight[best]) {
                best = i;
            }
        }
        if (weight[best] <= 0) {
            return fallback;
        }
        return Color.rgb((int) (r[best] / weight[best]), (int) (g[best] / weight[best]),
                (int) (bl[best] / weight[best]));
    }

    /** The screen background for an app: its colour, dark and toned down so it does not tire. */
    private static int backdrop(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] *= 0.70f;
        hsv[2] = 0.11f + 0.14f * hsv[2];
        return Color.HSVToColor(hsv);
    }

    private void setBackgroundColor(int color) {
        if (background.getColor() != color) {
            background.setColor(color);
        }
    }

    // --------------------------------------------------------------- refresh

    private void refresh() {
        pinRow.removeAllViews();
        grid.removeAllViews();
        tiles.clear();
        gridOrder.clear();
        focusTile = null;
        int gap = px(6);

        // Every pinned tile takes an equal share of the row, so one or two of them stretch.
        for (String pkg : pins) {
            App a = find(pkg);
            if (a == null) {
                continue;
            }
            ensureBig(a, wideTiles());
            TileView tile = new TileView(this, a, true, null);
            tiles.add(tile);
            pinRow.addView(tile, tileParams(gap));
        }
        if (pinRow.getChildCount() == 0) {
            TextView empty = text(20, TEXT_SOFT);
            empty.setGravity(Gravity.CENTER);
            empty.setText(S.t("Inicio vacío. Entrá al lápiz y elegí apps de la lista", "Home is empty. Open the pencil and pick apps from the list"));
            GradientDrawable outline = new GradientDrawable();
            outline.setCornerRadius(12 * u);
            outline.setStroke(px(2), 0x59FFFFFF, px(9), px(7));
            empty.setBackground(outline);
            pinRow.addView(empty, tileParams(gap));
        }

        for (App a : apps) {
            if (pins.contains(a.pkg)) {
                continue;
            }
            a.big = null;
            if (!hidden.contains(a.pkg)) {
                gridOrder.add(a);
            }
        }
        Collections.sort(gridOrder, new Comparator<App>() {
            @Override
            public int compare(App x, App y) {
                if (x.sortUses != y.sortUses) {
                    return y.sortUses - x.sortUses;
                }
                return x.label.compareToIgnoreCase(y.label);
            }
        });

        int cells = gridOrder.size() + (hidden.isEmpty() ? 0 : 1);
        LinearLayout row = null;
        for (int i = 0; i < cells; i++) {
            if (i % COLUMNS == 0) {
                row = box(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, px(60));
                lp.bottomMargin = px(8);
                grid.addView(row, lp);
            }
            TileView tile = i < gridOrder.size()
                    ? new TileView(this, gridOrder.get(i), false, null)
                    : new TileView(this, null, false, S.t("Apps ocultas (", "Hidden apps (") + hidden.size() + ")");
            tiles.add(tile);
            row.addView(tile, tileParams(gap));
        }
        if (row != null) {
            // Empty cells keep the last row's tiles the same width as the others.
            for (int i = cells; i % COLUMNS != 0; i++) {
                row.addView(new View(this), tileParams(gap));
            }
        }
        restoreFocus();
    }

    private LinearLayout.LayoutParams tileParams(int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = gap;
        lp.rightMargin = gap;
        return lp;
    }

    /** Puts focus back on the app that had it, or on the first tile. */
    private void restoreFocus() {
        TileView target = null;
        for (TileView t : tiles) {
            if (t.app != null && t.app.pkg.equals(focusPkg)) {
                target = t;
                break;
            }
        }
        if (target == null && !tiles.isEmpty()) {
            target = tiles.get(0);
        }
        focusLater(target);
    }

    private void focusLater(final View v) {
        if (v != null) {
            v.post(new Runnable() {
                @Override
                public void run() {
                    v.requestFocus();
                }
            });
        }
    }

    private void redrawPins() {
        for (int i = 0; i < pinRow.getChildCount(); i++) {
            pinRow.getChildAt(i).invalidate();
        }
        pencil.invalidate();
    }

    // ------------------------------------------------------ what tiles ask

    boolean isEditing() {
        return editing;
    }

    boolean isReplacing() {
        return replacing != null;
    }

    String grabbedPkg() {
        return grabbed;
    }

    /** One or two pinned apps: their cards stretch and show the name beside the art. */
    boolean wideTiles() {
        return pins.size() <= 2;
    }

    // ----------------------------------------------------------- focus, hero

    void onTileFocus(TileView t) {
        focusTile = t;
        if (t.app != null) {
            focusPkg = t.app.pkg;
            setBackgroundColor(editing || replacing != null ? NEUTRAL_BG : backdrop(t.app.color));
            setHero(t.app.label, Usage.describe(prefs, t.app.pkg, System.currentTimeMillis()));
        } else {
            setBackgroundColor(NEUTRAL_BG);
            setHero(S.t("Apps ocultas", "Hidden apps"), S.t("Volvé a mostrar las apps que ocultaste", "Show the apps you hid again"));
        }
        updateGuide();
    }

    void onTileBlur(TileView t) {
        // A grabbed tile that loses focus some other way (pointer, dialog) is put down.
        if (grabbed != null && t.app != null && t.app.pkg.equals(grabbed)) {
            grabbed = null;
            t.invalidate();
        }
    }

    void onPencilFocus() {
        chromeFocus(editing ? S.t("Salir de edición", "Leave editing") : S.t("Editar inicio", "Edit home"),
                S.t("Agregá, quitá o mové las apps de la fila de inicio", "Add, take out or move the apps in the home row"),
                editing ? S.t("OK: salir de edición", "OK: leave editing") : S.t("OK: entrar a edición", "OK: start editing"));
    }

    void onPencilClick() {
        setEditing(!editing);
    }

    /** Focus moved to a button rather than an app. */
    private void chromeFocus(String title, String sub, String guideText) {
        focusTile = null;
        chromeGuide = guideText;
        setBackgroundColor(NEUTRAL_BG);
        setHero(title, sub);
        updateGuide();
    }

    private void setHero(String title, String sub) {
        heroTitle.setText(title);
        heroSub.setText(sub);
    }

    private void refreshHero() {
        if (focusTile != null && focusTile.app != null) {
            heroSub.setText(Usage.describe(prefs, focusTile.app.pkg, System.currentTimeMillis()));
        }
    }

    /** The key hints at the bottom follow the focus and the mode. */
    private void updateGuide() {
        TileView t = focusTile;
        String g;
        if (replacing != null) {
            g = S.t("OK: reemplazar esta app por ", "OK: replace this app with ") + replacing.label + SEP + S.t("Atrás: cancelar", "Back: cancel");
        } else if (editing) {
            if (t == null || t.app == null) {
                g = t == null ? chromeGuide + SEP + guideExit() : guideExit();
            } else if (t.big && t.app.pkg.equals(grabbed)) {
                g = S.t("Izquierda / derecha: mover", "Left or right: move") + SEP + S.t("Abajo: quitar de inicio", "Down: take out of home") + SEP + S.t("OK: soltar", "OK: drop");
            } else if (t.big) {
                g = S.t("OK: agarrar para mover o quitar", "OK: grab to move or take out") + SEP + S.t("Abajo: ir a la lista", "Down: go to the list") + SEP + guideExit();
            } else {
                g = (pins.size() < MAX_PIN ? S.t("OK: subir a inicio", "OK: add to home")
                        : S.t("OK: subir a inicio reemplazando otra", "OK: add to home in place of another")) + SEP + guideExit();
            }
        } else if (t == null) {
            g = chromeGuide;
        } else if (t.app == null) {
            g = S.t("OK: ver las apps ocultas", "OK: see the hidden apps");
        } else {
            g = guideHome();
        }
        guide.setText(g);
    }

    // ------------------------------------------------------------- edit mode

    private void setEditing(boolean on) {
        editing = on;
        grabbed = null;
        replacing = null;
        inicioLabel.setText(on ? S.t("Editando inicio", "Editing home") : S.t("Inicio", "Home"));
        inicioLabel.setTextColor(on ? AMBER : TEXT_SOFT);
        redrawPins();
        if (on) {
            setBackgroundColor(NEUTRAL_BG);
            if (!tiles.isEmpty()) {
                tiles.get(0).requestFocus();
            }
        } else if (focusTile != null) {
            onTileFocus(focusTile);
        } else if (pencil.isFocused()) {
            onPencilFocus();
        }
        updateGuide();
    }

    /**
     * Key presses on a tile while editing or while picking which pinned app to replace.
     * Returns true when the key was used and the tile must not act on it.
     */
    boolean onTileKey(TileView t, int keyCode, KeyEvent event) {
        boolean ok = TileView.isOk(keyCode);
        boolean first = event.getRepeatCount() == 0;
        if (replacing != null) {
            if (ok) {
                if (first && t.big && t.app != null) {
                    replaceWith(t.app);
                }
                return true;
            }
            // Stay inside the pinned row until the user picks or cancels.
            return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_MENU;
        }
        if (!editing) {
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            return true;
        }
        if (t.app == null) {
            return ok;
        }
        if (!t.big) {
            if (ok && first) {
                addFromGrid(t.app);
            }
            return ok;
        }
        boolean held = t.app.pkg.equals(grabbed);
        if (ok) {
            if (first) {
                grabbed = held ? null : t.app.pkg;
                t.invalidate();
                updateGuide();
            }
            return true;
        }
        if (!held) {
            return false;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                moveGrabbed(t, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveGrabbed(t, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                removeGrabbed();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                return true;
            default:
                return false;
        }
    }

    /** The same actions for a pointer click, which carries no key. */
    private void editClick(TileView t) {
        if (t.app == null) {
            return;
        }
        if (replacing != null) {
            if (t.big) {
                replaceWith(t.app);
            }
        } else if (t.big) {
            grabbed = t.app.pkg.equals(grabbed) ? null : t.app.pkg;
            t.invalidate();
            updateGuide();
        } else {
            addFromGrid(t.app);
        }
    }

    private void moveGrabbed(TileView t, int direction) {
        int from = pins.indexOf(grabbed);
        int to = from + direction;
        if (from < 0 || to < 0 || to >= pins.size() || !(pinRow.getChildAt(to) instanceof TileView)) {
            return;
        }
        Collections.swap(pins, from, to);
        savePins();
        // Swap what the two views show instead of rebuilding the row, then follow the app.
        TileView other = (TileView) pinRow.getChildAt(to);
        App moving = t.app;
        t.app = other.app;
        other.app = moving;
        t.invalidate();
        other.invalidate();
        other.requestFocus();
    }

    private void removeGrabbed() {
        String pkg = grabbed;
        grabbed = null;
        pins.remove(pkg);
        focusPkg = pkg;
        commitPins();
    }

    private void addFromGrid(App a) {
        if (pins.size() >= MAX_PIN) {
            startReplacing(a);
            return;
        }
        // Keep the cursor in the list, on the app that slides into this slot.
        int i = gridOrder.indexOf(a);
        App next = i + 1 < gridOrder.size() ? gridOrder.get(i + 1) : i > 0 ? gridOrder.get(i - 1) : a;
        pins.add(a.pkg);
        focusPkg = next.pkg;
        commitPins();
    }

    private void startReplacing(App incoming) {
        if (pins.isEmpty() || !(pinRow.getChildAt(0) instanceof TileView)) {
            return;
        }
        replacing = incoming;
        setBackgroundColor(NEUTRAL_BG);
        redrawPins();
        updateGuide();
        focusLater(pinRow.getChildAt(0));
    }

    private void replaceWith(App outgoing) {
        int i = pins.indexOf(outgoing.pkg);
        if (i < 0 || replacing == null) {
            return;
        }
        pins.set(i, replacing.pkg);
        focusPkg = replacing.pkg;
        replacing = null;
        commitPins();
    }

    // ----------------------------------------------------------- tile events

    void onTileClick(final TileView t) {
        if (editing || replacing != null) {
            editClick(t);
            return;
        }
        if (t.app == null) {
            showHidden();
            return;
        }
        if (openingTile != null) {
            return;
        }
        // Show the opening notice first: on a slow box an app can take seconds to appear.
        t.opening = true;
        t.invalidate();
        openingTile = t;
        final App a = t.app;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!launch(a)) {
                    clearOpening.run();
                }
            }
        }, 60);
        handler.postDelayed(clearOpening, OPENING_TIMEOUT_MS);
    }

    void onTileMenu(TileView t) {
        if (editing || replacing != null) {
            return;
        }
        if (t.app == null) {
            showHidden();
        } else {
            showMenu(t.app);
        }
    }

    private boolean launch(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(a.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!open(i)) {
            return false;
        }
        a.uses++;
        prefs.edit().putInt(PREF_USES + a.pkg, a.uses).apply();
        long now = System.currentTimeMillis();
        Usage.beginSession(prefs, a.pkg, now, SystemClock.elapsedRealtime());
        handler.removeCallbacks(heartbeat);
        handler.postDelayed(heartbeat, HEARTBEAT_MS);
        return true;
    }

    private boolean open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            toast(S.t("No se pudo abrir", "Could not open it"));
            return false;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ menu

    private void showMenu(final App a) {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        final int index = pins.indexOf(a.pkg);

        names.add(S.t("Abrir", "Open"));
        actions.add(new Runnable() {
            @Override
            public void run() {
                launch(a);
            }
        });

        if (index >= 0) {
            if (index > 0) {
                names.add(S.t("Mover a la izquierda", "Move left"));
                actions.add(new Runnable() {
                    @Override
                    public void run() {
                        Collections.swap(pins, index, index - 1);
                        commitPins();
                    }
                });
            }
            if (index < pins.size() - 1) {
                names.add(S.t("Mover a la derecha", "Move right"));
                actions.add(new Runnable() {
                    @Override
                    public void run() {
                        Collections.swap(pins, index, index + 1);
                        commitPins();
                    }
                });
            }
            names.add(S.t("Quitar de inicio", "Take out of home"));
            actions.add(new Runnable() {
                @Override
                public void run() {
                    pins.remove(a.pkg);
                    commitPins();
                }
            });
        } else {
            if (pins.size() < MAX_PIN) {
                names.add(S.t("Subir a inicio", "Add to home"));
                actions.add(new Runnable() {
                    @Override
                    public void run() {
                        pins.add(a.pkg);
                        commitPins();
                    }
                });
            }
            if (!pins.isEmpty()) {
                names.add(S.t("Reemplazar en inicio", "Replace in home"));
                actions.add(new Runnable() {
                    @Override
                    public void run() {
                        startReplacing(a);
                    }
                });
            }
        }

        names.add(S.t("Info de la app", "App info"));
        actions.add(new Runnable() {
            @Override
            public void run() {
                appInfo(a);
            }
        });

        names.add(S.t("Ocultar de la lista", "Hide from the list"));
        actions.add(new Runnable() {
            @Override
            public void run() {
                hidden.add(a.pkg);
                pins.remove(a.pkg);
                saveHidden();
                commitPins();
            }
        });

        // The one action that removes something goes last, apart from the rest.
        int destructive = names.size();
        if (a.system) {
            // Factory apps cannot be uninstalled; their info screen has the Disable button.
            names.add(S.t("Deshabilitar (app de sistema)", "Disable (system app)"));
            actions.add(new Runnable() {
                @Override
                public void run() {
                    appInfo(a);
                }
            });
        } else {
            names.add(S.t("Desinstalar", "Uninstall"));
            actions.add(new Runnable() {
                @Override
                public void run() {
                    open(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)));
                }
            });
        }
        menu(a.label, names, actions, destructive);
    }

    private void showHidden() {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        for (final String pkg : new ArrayList<>(hidden)) {
            App a = find(pkg);
            names.add(a == null ? pkg : a.label);
            actions.add(new Runnable() {
                @Override
                public void run() {
                    hidden.remove(pkg);
                    focusPkg = pkg;
                    saveHidden();
                    refresh();
                }
            });
        }
        menu(S.t("Apps ocultas: elegí una para mostrarla", "Hidden apps: pick one to show it"), names, actions, names.size());
    }

    /**
     * A list menu in the launcher's own look. Rows from {@code destructiveFrom} on are set apart
     * by a line and drawn in red.
     */
    private void menu(String title, List<String> names, final List<Runnable> actions, int destructiveFrom) {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        LinearLayout column = box(LinearLayout.VERTICAL);
        column.setPadding(px(16), px(16), px(16), px(16));

        TextView heading = text(26, TEXT);
        heading.setTypeface(MEDIUM);
        heading.setText(title);
        heading.setPadding(px(14), px(4), px(14), px(12));
        column.addView(heading);

        for (int i = 0; i < names.size(); i++) {
            if (i == destructiveFrom) {
                View line = new View(this);
                line.setBackgroundColor(0x33FFFFFF);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, px(1)));
                lp.setMargins(px(14), px(8), px(14), px(8));
                column.addView(line, lp);
            }
            final int index = i;
            final TextView row = text(22, i >= destructiveFrom ? RED : TEXT);
            row.setText(names.get(i));
            row.setPadding(px(14), px(10), px(14), px(10));
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackground(pill(false, 0x00FFFFFF));
            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    row.setBackground(pill(hasFocus, 0x00FFFFFF));
                }
            });
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    actions.get(index).run();
                }
            });
            column.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(column);
        dialog.setContentView(scroller);
        Window window = dialog.getWindow();
        if (window != null) {
            GradientDrawable panel = new GradientDrawable();
            panel.setColor(0xF220212B);
            panel.setCornerRadius(14 * u);
            window.setBackgroundDrawable(panel);
            window.setLayout(px(560), LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private void appInfo(App a) {
        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
    }

    // ----------------------------------------------------------- persistence

    private static void split(String joined, ArrayList<String> out) {
        for (String s : joined.split(",")) {
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
    }

    private void savePins() {
        prefs.edit().putString(PREF_PINS, TextUtils.join(",", pins)).apply();
    }

    private void commitPins() {
        savePins();
        refresh();
    }

    private void saveHidden() {
        prefs.edit().putString(PREF_HIDDEN, TextUtils.join(",", hidden)).apply();
    }

    // ----------------------------------------------------------------- usage

    /** Reads Android's usage log into the ledger, off the main thread. */
    private void syncUsage() {
        if (syncing || !Usage.hasAccess(this)) {
            return;
        }
        syncing = true;
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Usage.sync(app, prefs);
                } catch (RuntimeException e) {
                    // The ledger simply keeps what it had.
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        syncing = false;
                        refreshHero();
                    }
                });
            }
        }, "usage-sync").start();
    }

    // ---------------------------------------------------------------- status

    private void updateClock(long now) {
        calendar.setTimeInMillis(now);
        clockText.setLength(0);
        two(calendar.get(Calendar.HOUR_OF_DAY)).append(':');
        two(calendar.get(Calendar.MINUTE)).append(':');
        two(calendar.get(Calendar.SECOND));
        clock.setText(clockText);
        int day = calendar.get(Calendar.DAY_OF_YEAR);
        if (day != lastDay) {
            lastDay = day;
            int weekday = calendar.get(Calendar.DAY_OF_WEEK) - 1;
            int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            int month = calendar.get(Calendar.MONTH);
            date.setText(S.spanish()
                    ? DAYS_ES[weekday] + " " + dayOfMonth + " de " + MONTHS_ES[month]
                    : DAYS_EN[weekday] + ", " + MONTHS_EN[month] + " " + dayOfMonth);
        }
    }

    private StringBuilder two(int n) {
        if (n < 10) {
            clockText.append('0');
        }
        return clockText.append(n);
    }

    /**
     * Free memory in bytes, from the kernel's MemAvailable. ActivityManager's own figure is not
     * used: this box's firmware reports 4 GB of RAM it does not have, which inflates it.
     */
    private long availableMemory() {
        try (BufferedReader in = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("MemAvailable:")) {
                    String digits = line.replaceAll("[^0-9]", "");
                    return Long.parseLong(digits) * 1024;
                }
            }
        } catch (IOException | NumberFormatException e) {
            // Fall through to the platform figure.
        }
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return mi.availMem;
    }

    private void updateStatus() {
        String name = S.t("Sin Wi-Fi", "No Wi-Fi");
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo info = wm == null ? null : wm.getConnectionInfo();
        if (info != null && info.getNetworkId() != -1 && info.getSSID() != null
                && !info.getSSID().contains("unknown")) {
            name = info.getSSID().replace("\"", "");
        }
        network.setText(name + SEP);
        long mb = availableMemory() / 1048576;
        ram.setText(S.t("RAM libre ", "Free RAM ") + mb + " MB");
        ram.setTextColor(mb < RAM_CRITICAL_MB ? RED : mb < RAM_LOW_MB ? AMBER : TEXT_SOFT);
    }

    /** Asks Android to drop every background process it is willing to drop. */
    private void freeMemory() {
        final long before = availableMemory();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        String self = getPackageName();
        for (ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
            if (!self.equals(ai.packageName)) {
                am.killBackgroundProcesses(ai.packageName);
            }
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long freed = (availableMemory() - before) / 1048576;
                toast(freed > 0 ? S.t("Liberados ", "Freed ") + freed + " MB" : S.t("Nada para liberar", "Nothing to free"));
                updateStatus();
            }
        }, 1000);
    }
}

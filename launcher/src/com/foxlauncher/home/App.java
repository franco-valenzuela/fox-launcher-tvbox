package com.foxlauncher.home;

import android.content.ComponentName;
import android.graphics.Bitmap;

/** One launchable app as shown on the home screen. */
final class App {
    String pkg;
    ComponentName component;
    String label;
    boolean system;
    /** Small square icon, always loaded. */
    Bitmap icon;
    /** Large art (TV banner or big icon), loaded only while the app is pinned to the top row. */
    Bitmap big;
    boolean bigIsBanner;
    /** Whether {@link #big} was loaded for the stretched one-or-two-tile layout. */
    boolean bigWide;
    /** Background colour of the banner, used for the whole card; 0 when there is no opaque one. */
    int cardColor;
    /** Dominant colour of the icon; the screen background is a muted version of it. */
    int color;
    /** Times opened from this launcher. */
    int uses;
    /** {@link #uses} as it was when the launcher started; the grid keeps this order until restart. */
    int sortUses;
}

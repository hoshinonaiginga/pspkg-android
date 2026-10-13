package io.github.hoshinonaiginga.pspkg;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class Util {
    private Util() {}

    static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int i = -1;
        do {
            v /= 1024.0;
            i++;
        } while (v >= 1024 && i < units.length - 1);
        return String.format(Locale.getDefault(), "%.2f %s", v, units[i]);
    }

    static boolean isPkg(File f) {
        return f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".pkg");
    }

    /** Recursively collects .pkg files under dir, sorted by path. */
    static List<File> findPkgs(File dir) {
        List<File> out = new ArrayList<>();
        walk(dir, out, 0);
        Collections.sort(out);
        return out;
    }

    private static void walk(File dir, List<File> out, int depth) {
        if (depth > 12) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) {
                if (!c.getName().startsWith(".")) walk(c, out, depth + 1);
            } else if (isPkg(c)) {
                out.add(c);
            }
        }
    }

    /** Same rule as the desktop CLI: <base>/<TITLE_ID>, unless base already is that folder. */
    static File outputDirFor(File base, String titleId) {
        if (titleId == null || titleId.isEmpty() || base.getName().equals(titleId)) return base;
        return new File(base, titleId);
    }
}

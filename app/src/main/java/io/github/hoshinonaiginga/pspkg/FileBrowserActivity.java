package io.github.hoshinonaiginga.pspkg;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Minimal path-based file browser. A real path is needed because the native
 * core reads the PKG with stdio, and PKGs can be tens of GB (no copying).
 */
public class FileBrowserActivity extends Activity {
    public static final String EXTRA_MODE = "mode";      // "file" or "dir"
    public static final String EXTRA_START = "start";
    public static final String EXTRA_TITLE = "title";
    public static final String RESULT_PATH = "path";

    private static final class Entry {
        final File file;
        final String label;
        final String detail;
        final boolean isDir;

        Entry(File file, String label, String detail, boolean isDir) {
            this.file = file;
            this.label = label;
            this.detail = detail;
            this.isDir = isDir;
        }
    }

    private boolean dirMode;
    private File current; // null = list of storage roots
    private final List<Entry> entries = new ArrayList<>();
    private ArrayAdapter<Entry> adapter;
    private TextView pathView;
    private Button useButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        dirMode = "dir".equals(getIntent().getStringExtra(EXTRA_MODE));
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        setTitle(title != null ? title : getString(dirMode ? R.string.browser_title_dir : R.string.browser_title_file));

        pathView = findViewById(R.id.browser_path);
        useButton = findViewById(R.id.browser_use);
        Button cancel = findViewById(R.id.browser_cancel);
        ListView list = findViewById(R.id.browser_list);

        useButton.setVisibility(dirMode ? View.VISIBLE : View.GONE);
        useButton.setOnClickListener(v -> {
            if (current != null) finishWith(current);
        });
        cancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        adapter = new ArrayAdapter<Entry>(this, android.R.layout.simple_list_item_2, android.R.id.text1, entries) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                Entry e = getItem(position);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                t1.setText(e.label);
                t2.setText(e.detail);
                t2.setVisibility(e.detail.isEmpty() ? View.GONE : View.VISIBLE);
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Entry e = entries.get(position);
            if (e.file == null) {
                navigate(null);
            } else if (e.isDir) {
                navigate(e.file);
            } else {
                finishWith(e.file);
            }
        });

        String start = getIntent().getStringExtra(EXTRA_START);
        File startDir = start != null ? new File(start) : null;
        if (startDir != null && startDir.isFile()) startDir = startDir.getParentFile();
        navigate(startDir != null && startDir.isDirectory() && startDir.canRead() ? startDir : null);
    }

    @Override
    public void onBackPressed() {
        if (current == null) {
            super.onBackPressed();
        } else {
            navigate(parentOf(current));
        }
    }

    private void finishWith(File f) {
        Intent data = new Intent();
        data.putExtra(RESULT_PATH, f.getAbsolutePath());
        setResult(RESULT_OK, data);
        finish();
    }

    private List<File> roots() {
        List<File> roots = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            for (StorageVolume v : sm.getStorageVolumes()) {
                File d = v.getDirectory();
                if (d != null && !roots.contains(d)) roots.add(d);
            }
        }
        if (roots.isEmpty()) {
            roots.add(Environment.getExternalStorageDirectory());
            File[] app = getExternalFilesDirs(null);
            if (app != null) {
                for (File f : app) {
                    if (f == null) continue;
                    String p = f.getAbsolutePath();
                    int idx = p.indexOf("/Android/");
                    if (idx > 0) {
                        File r = new File(p.substring(0, idx));
                        if (!roots.contains(r)) roots.add(r);
                    }
                }
            }
        }
        return roots;
    }

    /** Returns the parent folder, or null when f is a storage root. */
    private File parentOf(File f) {
        for (File r : roots()) {
            if (r.getAbsolutePath().equals(f.getAbsolutePath())) return null;
        }
        File p = f.getParentFile();
        return (p != null && p.canRead()) ? p : null;
    }

    private void navigate(File dir) {
        current = dir;
        entries.clear();
        if (dir == null) {
            pathView.setText(R.string.browser_roots);
            useButton.setEnabled(false);
            List<File> roots = roots();
            for (int i = 0; i < roots.size(); i++) {
                File r = roots.get(i);
                String label = getString(i == 0 ? R.string.browser_internal : R.string.browser_external);
                entries.add(new Entry(r, label, r.getAbsolutePath(), true));
            }
        } else {
            pathView.setText(dir.getAbsolutePath());
            useButton.setEnabled(dir.canWrite());
            entries.add(new Entry(parentOf(dir), "⬆  ..", "", true));
            File[] children = dir.listFiles();
            if (children != null) {
                Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().toLowerCase(Locale.ROOT).compareTo(b.getName().toLowerCase(Locale.ROOT));
                });
                for (File c : children) {
                    if (c.getName().startsWith(".")) continue;
                    if (c.isDirectory()) {
                        entries.add(new Entry(c, "📁  " + c.getName(), "", true));
                    } else if (!dirMode && Util.isPkg(c)) {
                        entries.add(new Entry(c, "📦  " + c.getName(), Util.humanSize(c.length()), false));
                    }
                }
            } else {
                entries.add(new Entry(parentOf(dir), getString(R.string.browser_unreadable), "", true));
            }
        }
        adapter.notifyDataSetChanged();
    }
}

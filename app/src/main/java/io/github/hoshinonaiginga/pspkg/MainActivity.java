package io.github.hoshinonaiginga.pspkg;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_PICK_FILE = 1;
    private static final int REQ_PICK_DIR = 2;
    private static final int REQ_PICK_OUT = 3;
    private static final int REQ_PERMS = 10;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private View permBox;
    private TextView txtSource, txtInfo, txtOut, txtStatus, txtLog;
    private CheckBox chkSameDir;
    private Button btnPickFile, btnPickDir, btnPickOut, btnExtract;
    private ProgressBar progress;
    private ScrollView scroll;

    private final List<File> inputs = new ArrayList<>();
    private File customOut;
    private String lastLog = "";
    private boolean wasRunning = false;

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            ui.postDelayed(this, 300);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("pspkg", MODE_PRIVATE);

        permBox = findViewById(R.id.perm_box);
        txtSource = findViewById(R.id.txt_source);
        txtInfo = findViewById(R.id.txt_info);
        txtOut = findViewById(R.id.txt_out);
        txtStatus = findViewById(R.id.txt_status);
        txtLog = findViewById(R.id.txt_log);
        chkSameDir = findViewById(R.id.chk_same_dir);
        btnPickFile = findViewById(R.id.btn_pick_file);
        btnPickDir = findViewById(R.id.btn_pick_dir);
        btnPickOut = findViewById(R.id.btn_pick_out);
        btnExtract = findViewById(R.id.btn_extract);
        progress = findViewById(R.id.progress);
        scroll = findViewById(R.id.scroll);

        findViewById(R.id.btn_grant).setOnClickListener(v -> requestStorage());
        btnPickFile.setOnClickListener(v -> openBrowser("file", REQ_PICK_FILE, prefs.getString("last_in", null)));
        btnPickDir.setOnClickListener(v -> openBrowser("dir", REQ_PICK_DIR, prefs.getString("last_in", null)));
        btnPickOut.setOnClickListener(v -> openBrowser("dir", REQ_PICK_OUT, prefs.getString("last_out", null)));
        chkSameDir.setChecked(prefs.getBoolean("same_dir", true));
        chkSameDir.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("same_dir", checked).apply();
            updateUi();
        });
        String lastOut = prefs.getString("last_out", null);
        if (lastOut != null) customOut = new File(lastOut);
        btnExtract.setOnClickListener(v -> startExtraction());
        setupCredits();
        restoreState(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_PERMS + 1);
        }
        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        permBox.setVisibility(hasStorage() ? View.GONE : View.VISIBLE);
        ui.post(poller);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(poller);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        String[] paths = new String[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) paths[i] = inputs.get(i).getAbsolutePath();
        out.putStringArray("inputs", paths);
        out.putCharSequence("source", txtSource.getText());
        out.putCharSequence("info", txtInfo.getText());
    }

    private void restoreState(Bundle saved) {
        if (saved != null) {
            String[] paths = saved.getStringArray("inputs");
            if (paths != null) for (String p : paths) inputs.add(new File(p));
            CharSequence src = saved.getCharSequence("source");
            CharSequence info = saved.getCharSequence("info");
            if (src != null) txtSource.setText(src);
            if (info != null) txtInfo.setText(info);
        } else if (!ExtractService.jobSource.isEmpty()) {
            // Reopened from scratch while a job is running (or just finished):
            // show what is being / was extracted instead of "No PKG selected".
            txtSource.setText(ExtractService.jobSource);
        }
    }

    private void setupCredits() {
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        TextView credits = findViewById(R.id.txt_credits);
        credits.setText(Html.fromHtml(getString(R.string.credits_html)
                        + (version == null || version.isEmpty() ? "" : " · v" + version),
                Html.FROM_HTML_MODE_COMPACT));
        credits.setMovementMethod(LinkMovementMethod.getInstance());
    }

    // ---------- Permissions ----------

    private boolean hasStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permBox.setVisibility(hasStorage() ? View.GONE : View.VISIBLE);
    }

    // ---------- Picking ----------

    private void openBrowser(String mode, int req, String start) {
        if (!hasStorage()) {
            requestStorage();
            return;
        }
        Intent i = new Intent(this, FileBrowserActivity.class);
        i.putExtra(FileBrowserActivity.EXTRA_MODE, mode);
        if (start != null) i.putExtra(FileBrowserActivity.EXTRA_START, start);
        if (req == REQ_PICK_DIR) i.putExtra(FileBrowserActivity.EXTRA_TITLE, getString(R.string.browser_title_pkg_dir));
        if (req == REQ_PICK_OUT) i.putExtra(FileBrowserActivity.EXTRA_TITLE, getString(R.string.browser_title_out));
        startActivityForResult(i, req);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        String path = data.getStringExtra(FileBrowserActivity.RESULT_PATH);
        if (path == null) return;
        File f = new File(path);
        switch (requestCode) {
            case REQ_PICK_FILE:
                prefs.edit().putString("last_in", f.getParent()).apply();
                inputs.clear();
                inputs.add(f);
                txtSource.setText(f.getAbsolutePath());
                loadInfoSingle(f);
                break;
            case REQ_PICK_DIR:
                prefs.edit().putString("last_in", f.getAbsolutePath()).apply();
                scanDir(f);
                break;
            case REQ_PICK_OUT:
                prefs.edit().putString("last_out", f.getAbsolutePath()).apply();
                customOut = f;
                break;
        }
        updateUi();
    }

    private void loadInfoSingle(File f) {
        txtInfo.setText(R.string.info_reading);
        new Thread(() -> {
            NativeLib.Meta m = NativeLib.meta(f.getAbsolutePath());
            ui.post(() -> {
                if (!m.ok()) {
                    txtInfo.setText(getString(R.string.info_open_failed, m.error));
                } else {
                    txtInfo.setText(getString(R.string.info_meta, m.titleId, Util.humanSize(m.size)));
                }
                updateUi();
            });
        }).start();
    }

    private void scanDir(File dir) {
        inputs.clear();
        txtSource.setText(getString(R.string.source_folder, dir.getAbsolutePath()));
        txtInfo.setText(R.string.scan_searching);
        new Thread(() -> {
            List<File> found = Util.findPkgs(dir);
            ui.post(() -> {
                inputs.clear();
                inputs.addAll(found);
                if (found.isEmpty()) {
                    txtInfo.setText(R.string.scan_none);
                } else {
                    StringBuilder sb = new StringBuilder(getString(R.string.scan_found, found.size())).append('\n');
                    long total = 0;
                    for (File p : found) {
                        sb.append("• ").append(p.getName()).append("  (").append(Util.humanSize(p.length())).append(")\n");
                        total += p.length();
                    }
                    sb.append(getString(R.string.scan_total, Util.humanSize(total)));
                    txtInfo.setText(sb.toString());
                }
                updateUi();
            });
        }).start();
    }

    // ---------- Extraction ----------

    private void startExtraction() {
        if (ExtractService.running || inputs.isEmpty()) return;
        if (!hasStorage()) {
            requestStorage();
            return;
        }
        boolean sameDir = chkSameDir.isChecked();
        if (!sameDir && customOut == null) {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.dlg_need_out, getString(R.string.chk_same_dir)))
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        String[] paths = new String[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) paths[i] = inputs.get(i).getAbsolutePath();

        ExtractService.clearLog();
        ExtractService.jobSource = txtSource.getText().toString();
        lastLog = "";
        Intent i = new Intent(this, ExtractService.class);
        i.putExtra(ExtractService.EXTRA_INPUTS, paths);
        if (!sameDir) i.putExtra(ExtractService.EXTRA_OUT_BASE, customOut.getAbsolutePath());
        ExtractService.running = true; // reflect immediately in the UI
        ExtractService.phase = getString(R.string.phase_starting);
        ExtractService.filesDone = 0;
        ExtractService.filesTotal = 0;
        startForegroundService(i);
        updateUi();
    }

    private void refreshStatus() {
        boolean running = ExtractService.running;
        if (running) {
            int done = ExtractService.filesDone, total = ExtractService.filesTotal;
            String head = ExtractService.pkgCount > 1
                    ? getString(R.string.status_pkg_prefix, ExtractService.pkgIndex, ExtractService.pkgCount) : "";
            if (total > 0) {
                progress.setIndeterminate(false);
                progress.setMax(total);
                progress.setProgress(done);
                txtStatus.setText(head + getString(R.string.status_files, done, total));
            } else {
                progress.setIndeterminate(true);
                txtStatus.setText(head + ExtractService.phase + "…");
            }
            progress.setVisibility(View.VISIBLE);
        } else {
            progress.setVisibility(View.GONE);
            txtStatus.setText(ExtractService.phase);
        }
        String log = ExtractService.getLog();
        if (!log.equals(lastLog)) {
            lastLog = log;
            txtLog.setText(log);
            txtLog.setVisibility(log.isEmpty() ? View.GONE : View.VISIBLE);
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
        if (running != wasRunning) {
            wasRunning = running;
            updateUi();
        }
    }

    private void updateUi() {
        boolean running = ExtractService.running;
        boolean sameDir = chkSameDir.isChecked();
        btnPickOut.setEnabled(!sameDir && !running);
        txtOut.setText(sameDir
                ? getString(R.string.out_same_dir)
                : (customOut != null ? customOut.getAbsolutePath() + "/<TITLE ID>" : getString(R.string.out_none)));
        btnPickFile.setEnabled(!running);
        btnPickDir.setEnabled(!running);
        chkSameDir.setEnabled(!running);
        btnExtract.setEnabled(!running && !inputs.isEmpty());
        btnExtract.setText(running ? getString(R.string.btn_extracting)
                : (inputs.size() > 1 ? getString(R.string.btn_extract_n, inputs.size()) : getString(R.string.btn_extract)));
    }
}

package io.github.hoshinonaiginga.pspkg;

/** Thin wrapper over the native PKG core (libpspkg.so). */
public final class NativeLib {
    static {
        System.loadLibrary("pspkg");
    }

    private NativeLib() {}

    public interface ProgressListener {
        void onProgress(int done, int total);
    }

    /** Returns {error, titleId, pkgSize, fileCount, contentFlags}; error is "" on success. */
    public static native String[] readMeta(String pkgPath);

    /** Extracts the PKG into outDir. Returns null on success, otherwise an error message. */
    public static native String extract(String pkgPath, String outDir, ProgressListener listener);

    /** Parsed metadata of a PKG. */
    public static final class Meta {
        public final String error;
        public final String titleId;
        public final long size;
        public final int fileCount;

        Meta(String[] raw) {
            error = raw[0];
            titleId = raw[1];
            size = parseLong(raw[2]);
            fileCount = (int) parseLong(raw[3]);
        }

        public boolean ok() {
            return error == null || error.isEmpty();
        }

        private static long parseLong(String s) {
            try {
                return Long.parseLong(s);
            } catch (Exception e) {
                return 0;
            }
        }
    }

    public static Meta meta(String pkgPath) {
        return new Meta(readMeta(pkgPath));
    }
}

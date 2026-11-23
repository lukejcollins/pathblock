package arpa.homelab.pathblock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class BlockPageProvider extends ContentProvider {
    private static volatile long chromeReadAt;

    static long chromeReadAt() {
        return chromeReadAt;
    }

    static final Uri URI = Uri.parse("content://pathblock/blocked.html");
    private static final byte[] PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'">
              <title>Pathblock</title>
              <style>
                *{box-sizing:border-box}
                body{margin:0;min-height:100dvh;display:grid;place-items:center;padding:24px;
                     background:#0f1417;color:#e6eeec;font-family:Roboto,system-ui,sans-serif}
                main{text-align:center}
                .shield{display:grid;place-items:center;margin:0 auto 18px;width:68px;height:74px;
                        background:#b4c5ff;color:#152044;
                        clip-path:polygon(50% 0,96% 16%,91% 61%,76% 83%,50% 100%,24% 83%,9% 61%,4% 16%)}
                .bar{width:28px;height:5px;border-radius:3px;background:currentColor;transform:rotate(-40deg)}
                h1{font-size:30px;font-weight:500;margin:0}
                p{color:#aebeba;font-size:15px;margin:8px 0 0}
              </style>
            </head>
            <body><main><div class="shield" aria-hidden="true"><div class="bar"></div></div>
              <h1>Pathblock</h1><p>Page blocked</p></main></body></html>
            """.getBytes(StandardCharsets.UTF_8);

    @Override public boolean onCreate() {
        return true;
    }

    @Override public String getType(Uri uri) {
        return URI.equals(uri) ? "text/html" : null;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        if (!URI.equals(uri)) return null;
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = "blocked.html";
            if (OpenableColumns.SIZE.equals(columns[i])) values[i] = PAGE.length;
        }
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        cursor.addRow(values);
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!URI.equals(uri) || !"r".equals(mode)) throw new FileNotFoundException();
        var context = getContext();
        if (context == null) throw new FileNotFoundException();
        File page = new File(context.getCacheDir(), "block-" + Arrays.hashCode(PAGE) + ".html");
        try {
            synchronized (this) {
                if (page.length() != PAGE.length) {
                    try (var stream = new FileOutputStream(page)) {
                        stream.write(PAGE);
                    }
                }
                ParcelFileDescriptor descriptor =
                        ParcelFileDescriptor.open(page, ParcelFileDescriptor.MODE_READ_ONLY);
                // This provider is private. Only the fixed document is granted to Chrome.
                chromeReadAt = SystemClock.uptimeMillis();
                return descriptor;
            }
        } catch (IOException error) {
            FileNotFoundException unavailable = new FileNotFoundException("Local block page unavailable");
            unavailable.initCause(error);
            throw unavailable;
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only page");
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only page");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only page");
    }
}

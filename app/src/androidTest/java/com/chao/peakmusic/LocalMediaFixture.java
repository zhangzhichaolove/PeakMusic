package com.chao.peakmusic;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Platform-only helper also used against the real minified app. Creates/removes only owned rows. */
final class LocalMediaFixture implements AutoCloseable {
    final String token = Long.toString(System.nanoTime());
    final String root = "Music/PeakMusicBrowse-" + token + "/";
    final String aName = "browse-a-" + token + ".mp3", bName = "browse-b-" + token + ".mp3";
    final String cName = "browse-c-" + token + ".mp3", unknownName = "browse-unknown-" + token + ".mp3";
    final List<Uri> uris = new ArrayList<>();
    private final java.util.Set<Uri> deleted = new java.util.HashSet<>();
    final Uri a, b, c, unknown;
    final Context context, assets;
    LocalMediaFixture(Context context, Context assets) throws Exception {
        this.context = context; this.assets = assets;
        try {
            a = add("a", "First/Disc", aName); b = add("a", "First/Disc", bName);
            c = add("b", "Second/Disc", cName); unknown = add("unknown", "Unknown", unknownName);
        } catch (Exception error) { close(); throw error; }
    }
    Uri add(String asset, String folder, String name) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, root + folder + "/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        values.put(MediaStore.Audio.Media.IS_MUSIC, 1);
        Uri uri = context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("MediaStore insert failed");
        uris.add(uri);
        try (InputStream in = assets.getAssets().open("local-fixtures/" + asset + ".mp3");
             OutputStream out = context.getContentResolver().openOutputStream(uri)) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        ContentValues ready = new ContentValues(); ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
        context.getContentResolver().update(uri, ready, null, null);
        return uri;
    }
    void delete(Uri uri) {
        if (deleted.contains(uri)) return;
        context.getContentResolver().delete(uri, null, null);
        deleted.add(uri); // Android may throw on a second delete after the ownership row is gone.
    }
    @Override public void close() { for (Uri uri : uris) delete(uri); }
}

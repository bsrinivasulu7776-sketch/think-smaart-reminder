package com.thinksmaart.reminder;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

public class AudioProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (getContext() == null || !"custom".equals(uri.getLastPathSegment())) throw new FileNotFoundException();
        File f = new File(getContext().getFilesDir(), "custom_reminder_sound");
        if (!f.exists()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        if (getContext() == null) return "audio/*";
        return getContext().getSharedPreferences(NotificationHelper.PREFS, 0).getString("custom_mime", "audio/*");
    }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String so) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
}

package com.magazinelibrary.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Temporary transfer files, PDF validation, cover rendering and publishing to Downloads or SAF. */
final class Files {
    private Files() {}

    static File part(Context c, long id) {
        File d = new File(c.getFilesDir(), "transfers");
        d.mkdirs();
        return new File(d, id + ".part");
    }

    static File cover(Context c, long id) {
        File d = new File(c.getCacheDir(), "covers");
        d.mkdirs();
        return new File(d, id + ".jpg");
    }

    static int validate(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            byte[] head = new byte[5];
            if (in.read(head) != 5
                    || !new String(head, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new IOException(T.s(R.string.error_not_pdf));
            }
        }
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer pdf = new PdfRenderer(fd)) {
            if (pdf.getPageCount() == 0) throw new IOException(T.s(R.string.error_pdf_no_pages));
            return pdf.getPageCount();
        } catch (RuntimeException e) {
            throw new IOException(T.s(R.string.error_pdf_unreadable), e);
        }
    }

    static Uri publish(Context c, Store.Item item, File source) throws IOException {
        Uri uri = null;
        try {
            ContentResolver resolver = c.getContentResolver();
            if (!item.tree.isEmpty()) {
                Uri tree = Uri.parse(item.tree);
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(
                        tree, DocumentsContract.getTreeDocumentId(tree));
                uri = DocumentsContract.createDocument(
                        resolver, parent, "application/pdf", Names.fileName(item.title));
            } else {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, Names.fileName(item.title));
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Magazine Library");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            }
            if (uri == null) throw new IOException(T.s(R.string.error_create_file));
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = resolver.openOutputStream(uri, "w")) {
                if (out == null) throw new IOException(T.s(R.string.error_folder_readonly));
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            if (item.tree.isEmpty()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }
            return uri;
        } catch (Exception e) {
            if (uri != null) {
                try {
                    if (item.tree.isEmpty()) c.getContentResolver().delete(uri, null, null);
                    else DocumentsContract.deleteDocument(c.getContentResolver(), uri);
                } catch (Exception ignored) {
                }
            }
            throw new IOException(T.s(R.string.error_save_failed), e);
        }
    }

    static void deleteOutput(Context c, Uri uri) throws IOException {
        try {
            if (DocumentsContract.isDocumentUri(c, uri)) {
                if (!DocumentsContract.deleteDocument(c.getContentResolver(), uri)) {
                    throw new IOException(T.s(R.string.error_delete_output));
                }
            } else {
                c.getContentResolver().delete(uri, null, null);
            }
        } catch (Exception e) {
            throw new IOException(T.s(R.string.error_delete_output), e);
        }
    }

    static void remove(Context c, Store.Item item, boolean deleteFile) throws IOException {
        if (deleteFile && !item.uri.isEmpty()) {
            Uri uri = Uri.parse(item.uri);
            try {
                if (DocumentsContract.isDocumentUri(c, uri)) {
                    if (!DocumentsContract.deleteDocument(c.getContentResolver(), uri)) {
                        throw new IOException(T.s(R.string.error_file_delete));
                    }
                } else if (c.getContentResolver().delete(uri, null, null) == 0) {
                    throw new IOException(T.s(R.string.error_file_missing));
                }
            } catch (FileNotFoundException ignored) {
            } catch (Exception e) {
                throw new IOException(T.s(R.string.error_file_delete_failed), e);
            }
        }
        Store.get(c).remove(item.id);
        part(c, item.id).delete();
        cover(c, item.id).delete();
    }

    static void rename(Context c, Store.Item item, String title) throws IOException {
        ContentValues values = new ContentValues();
        String clean = Names.title(title);
        // Downloaded files belong to this app; keep the name in sync with the library entry.
        if (!item.url.isEmpty() && !item.uri.isEmpty()) {
            try {
                Uri uri = Uri.parse(item.uri);
                if (DocumentsContract.isDocumentUri(c, uri)) {
                    Uri renamed = DocumentsContract.renameDocument(
                            c.getContentResolver(), uri, Names.fileName(clean));
                    if (renamed == null) throw new IOException(T.s(R.string.error_rename));
                    values.put("uri", renamed.toString());
                } else {
                    ContentValues name = new ContentValues();
                    name.put(MediaStore.MediaColumns.DISPLAY_NAME, Names.fileName(clean));
                    if (c.getContentResolver().update(uri, name, null, null) == 0) {
                        throw new IOException(T.s(R.string.error_rename));
                    }
                }
            } catch (Exception e) {
                throw new IOException(T.s(R.string.error_rename_failed), e);
            }
        }
        values.put("title", clean);
        values.put("series", Names.series(clean));
        Store.get(c).update(item.id, values);
    }

    static void makeCover(Context c, long id, File file) {
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer pdf = new PdfRenderer(fd);
             PdfRenderer.Page page = pdf.openPage(0)) {
            int width = 240, height = Math.max(1, width * page.getHeight() / page.getWidth());
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            try (OutputStream out = new FileOutputStream(cover(c, id))) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
            }
            bitmap.recycle();
        } catch (Exception ignored) {
        }
    }
}

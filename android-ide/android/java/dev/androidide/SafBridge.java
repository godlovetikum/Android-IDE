// android-ide/android/java/dev/androidide/SafBridge.java
//
// Android Storage Access Framework wrapper for the IDE Rust JNI bridge.
//
// All methods are static and safe to call from any thread. The ContentResolver
// is obtained from a WeakReference<Context> stored at init() time.
//
// Initialization:
//   SafBridge.init(context) is called from Rust via JNI inside
//   saf::init_safe_bridge(activity_ptr) in modules/filesystem/src/saf.rs.
//   This happens during android_main() — BEFORE slint::android::init() —
//   so it is always initialized before any filesystem operation begins.
//
//   Initialization sequence (enforced by android_main in src/lib.rs):
//     1. JNI_OnLoad → saf::init_vm(vm)               (JavaVM stored globally)
//     2. android_main captures activity_ptr from AndroidApp
//     3. android_main → saf::init_safe_bridge(activity_ptr)
//                     → SafBridge.init(activity) via JNI  ← this class
//     4. slint::android::init(app)
//     5. run_ui() → FilesystemManager operations can now proceed
//
//   Do NOT call SafBridge.init() from Java — it is called exclusively from
//   Rust via JNI. There is no custom Java Activity in this app; the entry
//   point is android_main() in the android_ide_lib.so NativeActivity binary.
//
// SAF URI shapes:
//   Tree URI:     content://com.android.externalstorage.documents/tree/primary%3AMyProject
//   Document URI: content://com.android.externalstorage.documents/document/primary%3AMyProject%2FMain.kt
//
// Required Android permissions in AndroidManifest.xml: none beyond what
// Intent.ACTION_OPEN_DOCUMENT_TREE grants at runtime.

package dev.androidide;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

public final class SafBridge {

    private static final String TAG = "SafBridge";

    // Weak reference so we never prevent Activity GC after it is destroyed.
    private static WeakReference<Context> sContextRef;

    private SafBridge() {}

    // -----------------------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------------------

    /**
     * Store the application context for later ContentResolver access.
     *
     * Called from Rust via saf::init_safe_bridge() during android_main().
     * Do NOT call this from Java — initialization is managed by the Rust layer.
     */
    public static void init(Context context) {
        sContextRef = new WeakReference<>(context.getApplicationContext());
        Log.i(TAG, "SafBridge initialized");
    }

    private static ContentResolver resolver() {
        if (sContextRef == null) return null;
        Context ctx = sContextRef.get();
        return ctx != null ? ctx.getContentResolver() : null;
    }

    // -----------------------------------------------------------------------
    // Directory listing
    // -----------------------------------------------------------------------

    /**
     * List the immediate children of a SAF tree URI.
     *
     * @param treeUriString  SAF tree URI (content://...)
     * @return               JSON array: [{id, name, mimeType, size}, ...]
     *                       Each "id" value is a full document URI string.
     *                       Returns null on failure.
     */
    public static String listChildren(String treeUriString) {
        ContentResolver cr = resolver();
        if (cr == null) {
            Log.e(TAG, "listChildren: SafBridge not initialized");
            return null;
        }

        try {
            Uri treeUri     = Uri.parse(treeUriString);
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId);

            String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            };

            JSONArray results = new JSONArray();

            try (Cursor cursor = cr.query(childrenUri, projection, null, null, null)) {
                if (cursor == null) {
                    Log.e(TAG, "listChildren: cursor is null for " + treeUriString);
                    return null;
                }

                int idIdx   = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                int mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);
                int sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE);

                while (cursor.moveToNext()) {
                    String docId    = cursor.getString(idIdx);
                    String name     = cursor.getString(nameIdx);
                    String mimeType = cursor.getString(mimeIdx);
                    long   size     = cursor.isNull(sizeIdx) ? 0L : cursor.getLong(sizeIdx);

                    // Build the full document URI for this child from its document ID
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);

                    JSONObject obj = new JSONObject();
                    obj.put("id",       docUri.toString());
                    obj.put("name",     name     != null ? name     : "");
                    obj.put("mimeType", mimeType != null ? mimeType : "application/octet-stream");
                    obj.put("size",     size);
                    results.put(obj);
                }
            }

            return results.toString();

        } catch (Exception e) {
            Log.e(TAG, "listChildren failed for " + treeUriString + ": " + e.getMessage(), e);
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // File reading
    // -----------------------------------------------------------------------

    /**
     * Read the full contents of a SAF document URI into a byte array.
     *
     * @param documentUriString  SAF document URI (content://...)
     * @return                   File bytes, or null on failure
     */
    public static byte[] readFile(String documentUriString) {
        ContentResolver cr = resolver();
        if (cr == null) {
            Log.e(TAG, "readFile: SafBridge not initialized");
            return null;
        }

        try (InputStream is = cr.openInputStream(Uri.parse(documentUriString))) {
            if (is == null) {
                Log.e(TAG, "readFile: openInputStream returned null for " + documentUriString);
                return null;
            }
            return readAllBytes(is);
        } catch (Exception e) {
            Log.e(TAG, "readFile failed for " + documentUriString + ": " + e.getMessage(), e);
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // File writing
    // -----------------------------------------------------------------------

    /**
     * Write data to a SAF document URI, replacing its entire content.
     *
     * Uses open mode "wt" (write + truncate). The previous file content is
     * discarded before writing begins. For atomic writes on Android, this is
     * the correct approach — do NOT append and truncate manually.
     *
     * @param documentUriString  SAF document URI (content://...)
     * @param data               Bytes to write
     * @return                   true on success, false on failure
     */
    public static boolean writeFile(String documentUriString, byte[] data) {
        ContentResolver cr = resolver();
        if (cr == null) {
            Log.e(TAG, "writeFile: SafBridge not initialized");
            return false;
        }

        try (OutputStream os = cr.openOutputStream(Uri.parse(documentUriString), "wt")) {
            if (os == null) {
                Log.e(TAG, "writeFile: openOutputStream returned null for " + documentUriString);
                return false;
            }
            os.write(data);
            os.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "writeFile failed for " + documentUriString + ": " + e.getMessage(), e);
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Document creation
    // -----------------------------------------------------------------------

    /**
     * Create a new document inside a SAF tree.
     *
     * Pass mimeType = "vnd.android.document/directory" to create a directory.
     *
     * @param parentTreeUriString  SAF tree URI of the parent directory
     * @param displayName          Desired name (e.g. "Main.kt")
     * @param mimeType             MIME type (e.g. "text/x-kotlin")
     * @return                     Document URI of the new file, or null on failure
     */
    public static String createFile(String parentTreeUriString, String displayName, String mimeType) {
        ContentResolver cr = resolver();
        if (cr == null) {
            Log.e(TAG, "createFile: SafBridge not initialized");
            return null;
        }

        try {
            Uri newDoc = DocumentsContract.createDocument(
                cr,
                Uri.parse(parentTreeUriString),
                mimeType,
                displayName
            );
            return newDoc != null ? newDoc.toString() : null;
        } catch (Exception e) {
            Log.e(TAG, "createFile failed (parent=" + parentTreeUriString + ", name=" + displayName + "): " + e.getMessage(), e);
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Deletion
    // -----------------------------------------------------------------------

    /**
     * Delete a SAF document (file or empty directory).
     *
     * @param documentUriString  SAF document URI to delete
     * @return                   true if deleted successfully, false otherwise
     */
    public static boolean deleteDocument(String documentUriString) {
        ContentResolver cr = resolver();
        if (cr == null) {
            Log.e(TAG, "deleteDocument: SafBridge not initialized");
            return false;
        }

        try {
            return DocumentsContract.deleteDocument(cr, Uri.parse(documentUriString));
        } catch (Exception e) {
            Log.e(TAG, "deleteDocument failed for " + documentUriString + ": " + e.getMessage(), e);
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Rename
    // -----------------------------------------------------------------------

    /**
     * Rename a SAF document.
     *
     * IMPORTANT: Android may assign a different URI after renaming. Always use
     * the returned URI for subsequent operations, not the original one.
     *
     * @param documentUriString  SAF document URI to rename
     * @param newDisplayName     New display name
     * @return                   New document URI, or null on failure
     */
    public static String renameDocument(String documentUriString, String newDisplayName) {
        ContentResolver cr = resolver();
        if (cr == null) {
            Log.e(TAG, "renameDocument: SafBridge not initialized");
            return null;
        }

        try {
            Uri renamed = DocumentsContract.renameDocument(
                cr,
                Uri.parse(documentUriString),
                newDisplayName
            );
            return renamed != null ? renamed.toString() : null;
        } catch (Exception e) {
            Log.e(TAG, "renameDocument failed for " + documentUriString + ": " + e.getMessage(), e);
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Metadata queries
    // -----------------------------------------------------------------------

    /**
     * Get the display name of a SAF document from the ContentResolver.
     *
     * @param documentUriString  SAF document URI
     * @return                   Display name, or null on failure
     */
    public static String getDisplayName(String documentUriString) {
        return queryStringColumn(documentUriString, DocumentsContract.Document.COLUMN_DISPLAY_NAME);
    }

    /**
     * Get the MIME type of a SAF document from the ContentResolver.
     *
     * @param documentUriString  SAF document URI
     * @return                   MIME type string, or null on failure
     */
    public static String getMimeType(String documentUriString) {
        return queryStringColumn(documentUriString, DocumentsContract.Document.COLUMN_MIME_TYPE);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static String queryStringColumn(String documentUriString, String column) {
        ContentResolver cr = resolver();
        if (cr == null) return null;

        String[] projection = { column };
        try (Cursor cursor = cr.query(Uri.parse(documentUriString), projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "queryStringColumn(" + column + ") failed for " + documentUriString + ": " + e.getMessage(), e);
        }
        return null;
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(8192);
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }
}

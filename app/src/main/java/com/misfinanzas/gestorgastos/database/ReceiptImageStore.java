package com.misfinanzas.gestorgastos.database;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.misfinanzas.gestorgastos.util.ImageUtils;

import java.util.concurrent.Executor;

/**
 * PILAR B: Comprobante fotográfico guardado en la nube SIN costo.
 *
 * Firebase Storage exige el plan de pago (Blaze) desde 2024, así que la foto se
 * guarda dentro del propio documento de la transacción en Cloud Firestore
 * (plan gratuito Spark):
 *   1. Se reduce, se corrige la rotación y se comprime en JPEG (máx. ~250 KB).
 *   2. Se codifica en Base64 como "data URI" (data:image/jpeg;base64,...).
 *   3. Ese texto viaja en el campo "imageUrl" y Glide lo muestra directamente.
 *
 * Un documento de Firestore admite hasta 1 MiB, por eso se limita el tamaño.
 * Al eliminar la transacción, la foto se elimina con ella.
 */
public final class ReceiptImageStore {

    /** Tamaño máximo del JPEG antes de codificar (Base64 lo aumenta ~33 %). */
    public static final int MAX_JPEG_BYTES = 250 * 1024;
    public static final String DATA_URI_PREFIX = "data:image/jpeg;base64,";

    private static final int CHUNK = 3 * 8192; // múltiplo de 3: los trozos Base64 se pueden concatenar

    public interface Callback {
        /** Progreso de 0 a 100 con la etapa actual (se llama en el hilo principal). */
        void onProgress(int percent, @NonNull String stage);

        /** Foto lista para guardarse en Firestore como texto. */
        void onReady(@NonNull String dataUri, int jpegBytes);

        void onError(@NonNull Exception e);
    }

    private ReceiptImageStore() {
    }

    public static void prepare(@NonNull Context context, @NonNull Uri uri, @NonNull Executor executor,
                               @NonNull Handler mainHandler, @NonNull Callback callback) {
        final Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            try {
                mainHandler.post(() -> callback.onProgress(5, "Comprimiendo foto..."));
                byte[] jpeg = ImageUtils.compressForUpload(appContext, uri, MAX_JPEG_BYTES);

                StringBuilder encoded = new StringBuilder(DATA_URI_PREFIX.length() + (jpeg.length * 4 / 3) + 4);
                encoded.append(DATA_URI_PREFIX);
                for (int offset = 0; offset < jpeg.length; offset += CHUNK) {
                    int len = Math.min(CHUNK, jpeg.length - offset);
                    encoded.append(Base64.encodeToString(jpeg, offset, len, Base64.NO_WRAP));
                    final int percent = 40 + (int) (40.0 * (offset + len) / jpeg.length);
                    mainHandler.post(() -> callback.onProgress(percent, "Codificando foto... " + percent + "%"));
                }

                final String dataUri = encoded.toString();
                final int size = jpeg.length;
                mainHandler.post(() -> callback.onReady(dataUri, size));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public static boolean isDataUri(String value) {
        return value != null && value.startsWith("data:image");
    }
}

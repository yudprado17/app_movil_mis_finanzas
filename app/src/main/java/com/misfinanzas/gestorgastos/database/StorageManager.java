package com.misfinanzas.gestorgastos.database;

import androidx.annotation.NonNull;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

/**
 * PILAR B: Subida de archivos binarios (fotos de comprobantes) a Firebase Storage.
 * Ruta en el bucket: comprobantes/{uid}/{timestamp}.jpg
 */
public class StorageManager {

    public interface UploadCallback {
        /** Progreso de 0 a 100. */
        void onProgress(int percent);

        /** Subida terminada: URL pública de descarga y ruta dentro del bucket. */
        void onSuccess(@NonNull String downloadUrl, @NonNull String storagePath);

        void onError(@NonNull Exception e);
    }

    private final StorageReference rootRef;

    public StorageManager() {
        this.rootRef = FirebaseStorage.getInstance().getReference();
    }

    public UploadTask uploadReceipt(String uid, byte[] jpegBytes, UploadCallback callback) {
        String path = "comprobantes/" + uid + "/" + System.currentTimeMillis() + ".jpg";
        StorageReference ref = rootRef.child(path);
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build();

        UploadTask task = ref.putBytes(jpegBytes, metadata);
        task.addOnProgressListener(snapshot -> {
                    // Monitoreo de progreso de la subida
                    long total = snapshot.getTotalByteCount();
                    int percent = total > 0 ? (int) (100.0 * snapshot.getBytesTransferred() / total) : 0;
                    callback.onProgress(percent);
                })
                .continueWithTask(t -> {
                    if (!t.isSuccessful() && t.getException() != null) throw t.getException();
                    // Obtención del enlace de descarga (Download URL)
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> callback.onSuccess(uri.toString(), path))
                .addOnFailureListener(callback::onError);
        return task;
    }

    /** Borra una foto del bucket (al eliminar la transacción o reemplazar la foto). */
    public void deleteFile(String storagePath) {
        if (storagePath == null || storagePath.isEmpty()) return;
        rootRef.child(storagePath).delete();
    }
}

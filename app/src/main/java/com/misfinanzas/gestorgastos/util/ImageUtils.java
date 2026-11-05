package com.misfinanzas.gestorgastos.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Prepara la foto antes de subirla: la reduce a un tamaño razonable, corrige la
 * rotación de la cámara (EXIF) y la comprime en JPEG. Así la subida es rápida y
 * se gasta menos almacenamiento en Firebase Storage.
 */
public final class ImageUtils {

    private static final int MAX_SIDE = 1280;
    private static final int JPEG_QUALITY = 80;

    private ImageUtils() {
    }

    /** Debe llamarse en un hilo secundario (lee y procesa el archivo). */
    public static byte[] compressForUpload(Context context, Uri uri) throws IOException {
        // 1. Leer solo las dimensiones para calcular el factor de reducción
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("El archivo seleccionado no es una imagen válida");
        }

        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) {
            sample *= 2;
        }

        // 2. Decodificar la imagen ya reducida
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) throw new IOException("No se pudo leer la imagen");

        // 3. Ajustar al tamaño máximo exacto
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float scale = Math.min(1f, (float) MAX_SIDE / Math.max(w, h));

        // 4. Corregir la rotación guardada por la cámara
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        int rotation = readRotation(context, uri);
        if (rotation != 0) matrix.postRotate(rotation);

        Bitmap result = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
        if (result != bitmap) bitmap.recycle();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        result.recycle();
        return out.toByteArray();
    }

    private static int readRotation(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }
}

package com.misfinanzas.gestorgastos.session;

import android.content.Context;
import android.content.SharedPreferences;

import com.misfinanzas.gestorgastos.model.Usuario;

/**
 * PILAR A: Control de sesión con SharedPreferences.
 *
 * Guarda localmente los datos básicos del usuario autenticado (uid, nombre,
 * correo y rol) para:
 *  - Hacer Auto-Login al abrir la app si la sesión sigue activa.
 *  - Aplicar el control de acceso por rol (RBAC) sin consultar Firestore cada vez.
 */
public class SessionManager {

    private static final String PREFS_NAME = "mis_finanzas_session";
    private static final String KEY_LOGGED_IN = "is_logged_in";
    private static final String KEY_UID = "uid";
    private static final String KEY_NAME = "nombre";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ROLE = "rol";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Persiste la sesión después de un login o registro exitoso. */
    public void saveSession(Usuario usuario) {
        prefs.edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_UID, usuario.getUid())
                .putString(KEY_NAME, usuario.getNombre())
                .putString(KEY_EMAIL, usuario.getEmail())
                .putString(KEY_ROLE, usuario.getRol())
                .apply();
    }

    /** Actualiza solo el rol (por ejemplo, si un administrador lo cambió en Firestore). */
    public void updateRole(String rol) {
        prefs.edit().putString(KEY_ROLE, rol).apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_LOGGED_IN, false);
    }

    public String getUid() {
        return prefs.getString(KEY_UID, null);
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, Usuario.ROL_ESTANDAR);
    }

    public boolean isAdmin() {
        return Usuario.ROL_ADMIN.equals(getRole());
    }

    /** Cierre de sesión: borra toda la caché local de la sesión. */
    public void clear() {
        prefs.edit().clear().apply();
    }
}

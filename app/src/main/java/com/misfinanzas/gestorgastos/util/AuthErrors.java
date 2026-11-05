package com.misfinanzas.gestorgastos.util;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/** Traduce los errores de FirebaseAuth a mensajes claros en español. */
public final class AuthErrors {

    private AuthErrors() {
    }

    public static String toMessage(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return "Sin conexión a internet. Revisa tu red e inténtalo de nuevo.";
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "La contraseña es muy débil (mínimo 6 caracteres).";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "Ya existe una cuenta con ese correo.";
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            return "No existe una cuenta con ese correo o fue deshabilitada.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Correo o contraseña incorrectos.";
        }
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
                return "El inicio de sesión con correo no está activado en la consola de Firebase.";
            }
            if ("ERROR_TOO_MANY_REQUESTS".equals(code)) {
                return "Demasiados intentos. Espera unos minutos e inténtalo de nuevo.";
            }
        }
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("CONFIGURATION_NOT_FOUND")) {
            return "Firebase Authentication no está configurado. Actívalo en la consola de Firebase.";
        }
        return "Error de autenticación: " + msg;
    }
}

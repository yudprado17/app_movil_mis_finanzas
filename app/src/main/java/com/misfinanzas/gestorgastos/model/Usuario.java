package com.misfinanzas.gestorgastos.model;

import com.google.firebase.firestore.Exclude;

/**
 * Perfil del usuario guardado en Firestore en la colección "usuarios/{uid}".
 * El campo "rol" define los permisos dentro de la app (RBAC).
 */
public class Usuario {

    public static final String ROL_ADMIN = "admin";
    public static final String ROL_ESTANDAR = "estandar";

    private String uid;
    private String nombre;
    private String email;
    private String rol;
    private long fechaRegistro;

    // Constructor vacío requerido por Firestore
    public Usuario() {
    }

    public Usuario(String uid, String nombre, String email, String rol) {
        this.uid = uid;
        this.nombre = nombre;
        this.email = email;
        this.rol = rol;
        this.fechaRegistro = System.currentTimeMillis();
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public long getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(long fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    @Exclude
    public boolean isAdmin() {
        return ROL_ADMIN.equals(rol);
    }

    /** Nombre legible del rol para mostrar en pantalla. */
    public static String roleLabel(String rol) {
        return ROL_ADMIN.equals(rol) ? "Administrador" : "Estándar";
    }
}

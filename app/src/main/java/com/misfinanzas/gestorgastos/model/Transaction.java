package com.misfinanzas.gestorgastos.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

public class Transaction {

    @DocumentId
    private String id;
    private String concepto;
    private double monto;
    private String tipo; // "Ingreso" or "Gasto"
    private String fecha; // Formato: YYYY-MM-DD
    private String categoria;

    // ---- Momento 3: dueño del registro (RBAC) ----
    private String userId;
    private String userEmail;

    // ---- Momento 3: comprobante fotográfico en Firebase Storage ----
    private String imageUrl;   // Download URL pública para Glide
    private String imagePath;  // Ruta dentro del bucket (para poder borrarla)

    // ---- Momento 3: geolocalización (null si no se capturó) ----
    private Double latitud;
    private Double longitud;
    private String direccion;

    // Constructor vacío requerido para la deserialización automática de Firebase Firestore POJO
    public Transaction() {
    }

    // Constructor sin ID (útil para crear nuevos documentos en Firestore)
    public Transaction(String concepto, double monto, String tipo, String fecha, String categoria) {
        this.concepto = concepto;
        this.monto = monto;
        this.tipo = tipo;
        this.fecha = fecha;
        this.categoria = categoria;
    }

    // Constructor completo con ID
    public Transaction(String id, String concepto, double monto, String tipo, String fecha, String categoria) {
        this.id = id;
        this.concepto = concepto;
        this.monto = monto;
        this.tipo = tipo;
        this.fecha = fecha;
        this.categoria = categoria;
    }

    @Exclude
    public boolean hasLocation() {
        return latitud != null && longitud != null;
    }

    @Exclude
    public boolean hasImage() {
        return imageUrl != null && !imageUrl.isEmpty();
    }

    @Exclude
    public boolean isIncome() {
        return "Ingreso".equalsIgnoreCase(tipo);
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConcepto() {
        return concepto;
    }

    public void setConcepto(String concepto) {
        this.concepto = concepto;
    }

    public double getMonto() {
        return monto;
    }

    public void setMonto(double monto) {
        this.monto = monto;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public Double getLatitud() {
        return latitud;
    }

    public void setLatitud(Double latitud) {
        this.latitud = latitud;
    }

    public Double getLongitud() {
        return longitud;
    }

    public void setLongitud(Double longitud) {
        this.longitud = longitud;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }
}

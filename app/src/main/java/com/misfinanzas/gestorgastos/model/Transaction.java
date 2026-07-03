package com.misfinanzas.gestorgastos.model;

public class Transaction {
    private int id;
    private String concepto;
    private double monto;
    private String tipo; // "Ingreso" or "Gasto"
    private String fecha; // Formato: YYYY-MM-DD
    private String categoria;

    // Constructor vacío
    public Transaction() {
    }

    // Constructor sin ID (útil para insertar nuevos registros)
    public Transaction(String concepto, double monto, String tipo, String fecha, String categoria) {
        this.concepto = concepto;
        this.monto = monto;
        this.tipo = tipo;
        this.fecha = fecha;
        this.categoria = categoria;
    }

    // Constructor completo con ID (para leer y actualizar registros existentes)
    public Transaction(int id, String concepto, double monto, String tipo, String fecha, String categoria) {
        this.id = id;
        this.concepto = concepto;
        this.monto = monto;
        this.tipo = tipo;
        this.fecha = fecha;
        this.categoria = categoria;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
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
}

package com.misfinanzas.gestorgastos.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.misfinanzas.gestorgastos.model.Transaction;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "MisFinanzas.db";
    private static final int DATABASE_VERSION = 1;

    // Tabla y columnas
    public static final String TABLE_NAME = "transacciones";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_CONCEPTO = "concepto";
    public static final String COLUMN_MONTO = "monto";
    public static final String COLUMN_TIPO = "tipo";
    public static final String COLUMN_FECHA = "fecha";
    public static final String COLUMN_CATEGORIA = "categoria";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_CONCEPTO + " TEXT, "
                + COLUMN_MONTO + " REAL, "
                + COLUMN_TIPO + " TEXT, "
                + COLUMN_FECHA + " TEXT, "
                + COLUMN_CATEGORIA + " TEXT"
                + ")";
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    // ==========================================
    // OPERACIONES CRUD
    // ==========================================

    // 1. INSERTAR (CREATE)
    public long insertTransaction(Transaction transaction) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_CONCEPTO, transaction.getConcepto());
        values.put(COLUMN_MONTO, transaction.getMonto());
        values.put(COLUMN_TIPO, transaction.getTipo());
        values.put(COLUMN_FECHA, transaction.getFecha());
        values.put(COLUMN_CATEGORIA, transaction.getCategoria());

        long id = db.insert(TABLE_NAME, null, values);
        db.close();
        return id;
    }

    // 2. LEER TODAS (READ ALL) - Ordenadas por fecha descendente
    public List<Transaction> getAllTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String selectQuery = "SELECT * FROM " + TABLE_NAME + " ORDER BY " + COLUMN_FECHA + " DESC, " + COLUMN_ID + " DESC";
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Transaction transaction = new Transaction();
                transaction.setId(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))));
                transaction.setConcepto(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONCEPTO)));
                transaction.setMonto(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_MONTO)));
                transaction.setTipo(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIPO)));
                transaction.setFecha(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FECHA)));
                transaction.setCategoria(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORIA)));
                
                transactions.add(transaction);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return transactions;
    }

    // 2b. LEER CON FILTRO (Búsqueda por concepto o categoría)
    public List<Transaction> searchTransactions(String query) {
        List<Transaction> transactions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String selectQuery = "SELECT * FROM " + TABLE_NAME + " WHERE " 
                + COLUMN_CONCEPTO + " LIKE ? OR " 
                + COLUMN_CATEGORIA + " LIKE ? ORDER BY " 
                + COLUMN_FECHA + " DESC";
        
        Cursor cursor = db.rawQuery(selectQuery, new String[]{"%" + query + "%", "%" + query + "%"});

        if (cursor.moveToFirst()) {
            do {
                Transaction transaction = new Transaction();
                transaction.setId(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))));
                transaction.setConcepto(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONCEPTO)));
                transaction.setMonto(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_MONTO)));
                transaction.setTipo(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIPO)));
                transaction.setFecha(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FECHA)));
                transaction.setCategoria(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORIA)));
                
                transactions.add(transaction);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return transactions;
    }

    // 3. LEER POR ID (READ BY ID)
    public Transaction getTransactionById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Transaction transaction = null;

        Cursor cursor = db.query(TABLE_NAME, 
                null, 
                COLUMN_ID + "=?", 
                new String[]{String.valueOf(id)}, 
                null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            transaction = new Transaction();
            transaction.setId(String.valueOf(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))));
            transaction.setConcepto(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONCEPTO)));
            transaction.setMonto(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_MONTO)));
            transaction.setTipo(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIPO)));
            transaction.setFecha(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FECHA)));
            transaction.setCategoria(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORIA)));
            cursor.close();
        }
        db.close();
        return transaction;
    }

    // 4. ACTUALIZAR (UPDATE)
    public int updateTransaction(Transaction transaction) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_CONCEPTO, transaction.getConcepto());
        values.put(COLUMN_MONTO, transaction.getMonto());
        values.put(COLUMN_TIPO, transaction.getTipo());
        values.put(COLUMN_FECHA, transaction.getFecha());
        values.put(COLUMN_CATEGORIA, transaction.getCategoria());

        int count = db.update(TABLE_NAME, values, COLUMN_ID + " = ?", new String[]{String.valueOf(transaction.getId())});
        db.close();
        return count;
    }

    // 5. ELIMINAR (DELETE)
    public int deleteTransaction(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int count = db.delete(TABLE_NAME, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return count;
    }

    // ==========================================
    // MÉTODOS DE CÁLCULO FINANCIERO
    // ==========================================

    // Sumar todos los ingresos
    public double getTotalIncome() {
        double total = 0;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(" + COLUMN_MONTO + ") FROM " + TABLE_NAME + " WHERE " + COLUMN_TIPO + " = 'Ingreso'", null);
        if (cursor.moveToFirst()) {
            total = cursor.getDouble(0);
        }
        cursor.close();
        db.close();
        return total;
    }

    // Sumar todos los gastos
    public double getTotalExpense() {
        double total = 0;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(" + COLUMN_MONTO + ") FROM " + TABLE_NAME + " WHERE " + COLUMN_TIPO + " = 'Gasto'", null);
        if (cursor.moveToFirst()) {
            total = cursor.getDouble(0);
        }
        cursor.close();
        db.close();
        return total;
    }
}

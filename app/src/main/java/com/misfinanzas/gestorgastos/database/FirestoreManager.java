package com.misfinanzas.gestorgastos.database;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.misfinanzas.gestorgastos.model.Transaction;

public class FirestoreManager {

    private static final String COLLECTION_NAME = "transacciones";
    private final FirebaseFirestore db;
    private final CollectionReference collectionRef;

    public FirestoreManager() {
        this.db = FirebaseFirestore.getInstance();
        this.collectionRef = db.collection(COLLECTION_NAME);
    }

    // =========================================================================
    // 1. ESCUCHA EN TIEMPO REAL (Real-time SnapshotListener)
    // =========================================================================
    /**
     * Registra un listener en tiempo real para obtener los documentos de la colección
     * ordenados por fecha de forma descendente.
     * 
     * @param listener Callback de escucha para eventos de cambio en Firestore.
     * @return ListenerRegistration para poder desregistrar en onDestroy() y evitar memory leaks.
     */
    public ListenerRegistration listenToTransactions(EventListener<QuerySnapshot> listener) {
        return collectionRef
                .orderBy("fecha", Query.Direction.DESCENDING)
                .addSnapshotListener(listener);
    }

    // =========================================================================
    // 2. CREATE (Guardar nueva transacción)
    // =========================================================================
    public void addTransaction(Transaction transaction, OnSuccessListener<DocumentReference> successListener, OnFailureListener failureListener) {
        collectionRef.add(transaction)
                .addOnSuccessListener(successListener)
                .addOnFailureListener(failureListener);
    }

    // =========================================================================
    // 3. READ BY ID (Obtener un documento por su Document ID)
    // =========================================================================
    public void getTransactionById(String id, OnSuccessListener<DocumentSnapshot> successListener, OnFailureListener failureListener) {
        collectionRef.document(id).get()
                .addOnSuccessListener(successListener)
                .addOnFailureListener(failureListener);
    }

    // =========================================================================
    // 4. UPDATE (Actualizar documento existente apuntando a su Document ID)
    // =========================================================================
    public void updateTransaction(String id, Transaction transaction, OnSuccessListener<Void> successListener, OnFailureListener failureListener) {
        collectionRef.document(id).set(transaction)
                .addOnSuccessListener(successListener)
                .addOnFailureListener(failureListener);
    }

    // =========================================================================
    // 5. DELETE (Eliminar documento apuntando a su Document ID)
    // =========================================================================
    public void deleteTransaction(String id, OnSuccessListener<Void> successListener, OnFailureListener failureListener) {
        collectionRef.document(id).delete()
                .addOnSuccessListener(successListener)
                .addOnFailureListener(failureListener);
    }
}

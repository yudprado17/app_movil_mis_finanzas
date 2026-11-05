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
import com.google.firebase.firestore.SetOptions;
import com.misfinanzas.gestorgastos.model.Transaction;
import com.misfinanzas.gestorgastos.model.Usuario;

import java.util.Collections;

public class FirestoreManager {

    private static final String COLLECTION_NAME = "transacciones";
    private static final String USERS_COLLECTION = "usuarios";
    private final FirebaseFirestore db;
    private final CollectionReference collectionRef;
    private final CollectionReference usersRef;

    public FirestoreManager() {
        this.db = FirebaseFirestore.getInstance();
        this.collectionRef = db.collection(COLLECTION_NAME);
        this.usersRef = db.collection(USERS_COLLECTION);
    }

    // =========================================================================
    // 1. ESCUCHA EN TIEMPO REAL (Real-time SnapshotListener) con RBAC
    // =========================================================================
    /**
     * Registra un listener en tiempo real sobre las transacciones visibles para el rol:
     *  - Administrador: todas las transacciones de todos los usuarios.
     *  - Estándar: solo las transacciones cuyo userId es el suyo.
     *
     * El orden por fecha se aplica en la app (no en la consulta) para no requerir
     * un índice compuesto en Firestore al combinar whereEqualTo + orderBy.
     *
     * @return ListenerRegistration para poder desregistrar en onDestroy() y evitar memory leaks.
     */
    public ListenerRegistration listenToTransactions(boolean isAdmin, String uid, EventListener<QuerySnapshot> listener) {
        Query query = isAdmin
                ? collectionRef
                : collectionRef.whereEqualTo("userId", uid);
        return query.addSnapshotListener(listener);
    }

    /** Ordena una lista de transacciones por fecha descendente (más recientes primero). */
    public static void sortByDateDesc(java.util.List<Transaction> list) {
        Collections.sort(list, (a, b) -> {
            String fa = a.getFecha() != null ? a.getFecha() : "";
            String fb = b.getFecha() != null ? b.getFecha() : "";
            return fb.compareTo(fa);
        });
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

    // =========================================================================
    // 6. PERFILES DE USUARIO (Colección "usuarios/{uid}" con el rol para RBAC)
    // =========================================================================
    public void getUser(String uid, OnSuccessListener<DocumentSnapshot> successListener, OnFailureListener failureListener) {
        usersRef.document(uid).get()
                .addOnSuccessListener(successListener)
                .addOnFailureListener(failureListener);
    }

    public void createUser(Usuario usuario, OnSuccessListener<Void> successListener, OnFailureListener failureListener) {
        usersRef.document(usuario.getUid()).set(usuario)
                .addOnSuccessListener(successListener)
                .addOnFailureListener(failureListener);
    }

    /** Guarda el token FCM del dispositivo en el perfil (útil para enviar push dirigidos). */
    public void saveFcmToken(String uid, String token) {
        usersRef.document(uid)
                .set(Collections.singletonMap("fcmToken", token), SetOptions.merge());
    }
}

package com.misfinanzas.gestorgastos.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.misfinanzas.gestorgastos.R;
import com.misfinanzas.gestorgastos.model.Transaction;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private final Context context;
    private List<Transaction> transactionList;
    private final OnItemClickListener listener;
    private final NumberFormat currencyFormatter;
    private final SimpleDateFormat inputDateFormat;
    private final SimpleDateFormat outputDateFormat;
    private boolean showOwner = false;

    public interface OnItemClickListener {
        void onItemClick(Transaction transaction);
    }

    public TransactionAdapter(Context context, List<Transaction> transactionList, OnItemClickListener listener) {
        this.context = context;
        this.transactionList = transactionList;
        this.listener = listener;
        this.currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);
        this.inputDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        this.outputDateFormat = new SimpleDateFormat("dd MMM yyyy", new Locale("es", "CO"));
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction transaction = transactionList.get(position);
        holder.bind(transaction, listener);
    }

    /**
     * Prevención del bug de reciclaje: cuando una tarjeta sale de la pantalla
     * se cancela la carga pendiente de Glide para que la imagen no aparezca
     * luego en otra tarjeta reutilizada.
     */
    @Override
    public void onViewRecycled(@NonNull TransactionViewHolder holder) {
        super.onViewRecycled(holder);
        Glide.with(context).clear(holder.ivReceipt);
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    public void updateData(List<Transaction> newList) {
        this.transactionList = newList;
        notifyDataSetChanged();
    }

    /** RBAC: el Administrador ve el correo del dueño de cada transacción. */
    public void setShowOwner(boolean showOwner) {
        this.showOwner = showOwner;
        notifyDataSetChanged();
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvConcept;
        private final TextView tvCategory;
        private final TextView tvDate;
        private final TextView tvAmount;
        private final TextView tvAddress;
        private final TextView tvOwner;
        private final ImageView ivTransactionIcon;
        private final ImageView ivReceipt;
        private final FrameLayout layoutIconContainer;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvConcept = itemView.findViewById(R.id.tv_concept);
            tvCategory = itemView.findViewById(R.id.tv_category);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvAmount = itemView.findViewById(R.id.tv_amount);
            tvAddress = itemView.findViewById(R.id.tv_address);
            tvOwner = itemView.findViewById(R.id.tv_owner);
            ivTransactionIcon = itemView.findViewById(R.id.iv_transaction_icon);
            ivReceipt = itemView.findViewById(R.id.iv_receipt);
            layoutIconContainer = itemView.findViewById(R.id.layout_icon_container);
        }

        public void bind(final Transaction transaction, final OnItemClickListener listener) {
            tvConcept.setText(transaction.getConcepto());
            tvCategory.setText(transaction.getCategoria());

            // Formatear Fecha
            String formattedDate = transaction.getFecha();
            if (formattedDate != null) {
                try {
                    Date date = inputDateFormat.parse(formattedDate);
                    if (date != null) {
                        formattedDate = outputDateFormat.format(date);
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
            tvDate.setText(formattedDate != null ? formattedDate : "");

            // Formatear Monto y estilo dinámico
            double amount = transaction.getMonto();
            String formattedAmount = currencyFormatter.format(amount);

            if ("Ingreso".equalsIgnoreCase(transaction.getTipo())) {
                layoutIconContainer.setBackgroundResource(R.drawable.bg_round_income);
                ivTransactionIcon.setImageResource(R.drawable.ic_income);
                ivTransactionIcon.setColorFilter(ContextCompat.getColor(context, R.color.income));

                tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income));
                tvAmount.setText("+ " + formattedAmount);
            } else {
                layoutIconContainer.setBackgroundResource(R.drawable.bg_round_expense);
                ivTransactionIcon.setImageResource(R.drawable.ic_expense);
                ivTransactionIcon.setColorFilter(ContextCompat.getColor(context, R.color.expense));

                tvAmount.setTextColor(ContextCompat.getColor(context, R.color.expense));
                tvAmount.setText("- " + formattedAmount);
            }

            // =================================================================
            // PILAR B: Renderizado eficiente con Glide
            //  - placeholder(): imagen por defecto mientras descarga
            //  - error(): imagen si la URL falla o no hay conexión
            //  - diskCacheStrategy(ALL): caché local en disco (no se re-descarga)
            //  - Siempre se asigna algo (o se limpia) para evitar el bug de reciclaje
            // =================================================================
            if (transaction.hasImage()) {
                ivReceipt.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivReceipt.setPadding(0, 0, 0, 0);
                Glide.with(context)
                        .load(transaction.getImageUrl())
                        .placeholder(R.drawable.ic_receipt)
                        .error(R.drawable.ic_broken_image)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .into(ivReceipt);
            } else {
                Glide.with(context).clear(ivReceipt);
                int pad = (int) (14 * context.getResources().getDisplayMetrics().density);
                ivReceipt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ivReceipt.setPadding(pad, pad, pad, pad);
                ivReceipt.setImageResource(R.drawable.ic_receipt);
            }

            // Dirección (geocodificación inversa)
            if (transaction.getDireccion() != null && !transaction.getDireccion().isEmpty()) {
                tvAddress.setText(transaction.getDireccion());
                tvAddress.setVisibility(View.VISIBLE);
            } else {
                tvAddress.setVisibility(View.GONE);
            }

            // Dueño (solo Administrador)
            if (showOwner) {
                String owner = transaction.getUserEmail() != null ? transaction.getUserEmail() : "Sin dueño (Momento 2)";
                tvOwner.setText(owner);
                tvOwner.setVisibility(View.VISIBLE);
            } else {
                tvOwner.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onItemClick(transaction));
        }
    }
}

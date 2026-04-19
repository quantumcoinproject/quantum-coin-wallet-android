package com.quantumcoinwallet.app.view.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.api.read.model.AccountTransactionSummary;
import com.quantumcoinwallet.app.utils.CoinUtils;
import com.quantumcoinwallet.app.utils.GlobalMethods;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import java.math.BigInteger;
import java.util.List;

public class AccountTransactionAdapter extends
        Adapter<AccountTransactionAdapter.DataObjectHolder> {

    private static final String TAG = "AccountTransactionAdapter";

    public List<AccountTransactionSummary> accountTransactionSummaries;

    public static String walletAddress;

    private Context context;

    class DataObjectHolder extends ViewHolder {

        ImageView imageViewInOut;

        TextView textViewTransHash;
        TextView textViewDate;

        TextView textViewFrom;
        TextView textViewTo;

        TextView textViewQuantity;

        public DataObjectHolder(View itemView) {
            super(itemView);
            try{
                this.imageViewInOut = (ImageView) itemView.findViewById(R.id.imageView_account_transactions_adapter_in_out);
                this.textViewQuantity = (TextView) itemView.findViewById(R.id.textView_account_transactions_adapter_quantity);
                this.textViewDate = (TextView) itemView.findViewById(R.id.textView_account_transactions_adapter_date);
                this.textViewFrom = (TextView) itemView.findViewById(R.id.textView_account_transactions_adapter_from);
                this.textViewTo = (TextView) itemView.findViewById(R.id.textView_account_transactions_adapter_to);
                this.textViewTransHash = (TextView) itemView.findViewById(R.id.textView_account_transactions_adapter_trans_hash);
            } catch(Exception ex){
                GlobalMethods.ExceptionError(context, TAG, ex);
            }
        }
    }

    public AccountTransactionAdapter(Context context,
                                     List<AccountTransactionSummary> accountTransactionSummaries,
                                     String walletAddress) {
        this.context = context;
        this.accountTransactionSummaries = accountTransactionSummaries;
        this.walletAddress = walletAddress;
    }

    @Override
    public DataObjectHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        return new DataObjectHolder(LayoutInflater.from(viewGroup.getContext()).inflate(
                R.layout.account_transactions_adapter, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(DataObjectHolder holder, @SuppressLint("RecyclerView") final int position) {
        try {
            AccountTransactionSummary txn = accountTransactionSummaries.get(position);

            String hash = txn.getHash() != null ? txn.getHash().toString() : "";
            String from = txn.getFrom() != null ? txn.getFrom().toString() : "";
            String to = txn.getTo() != null ? txn.getTo().toString() : "";
            String rawValue = txn.getValue() != null ? txn.getValue().toString() : null;
            String rawDate = txn.getCreatedAt() != null ? txn.getCreatedAt().toString() : null;

            if (walletAddress != null && from.length() > 0
                    && walletAddress.toLowerCase().equals(from.toLowerCase())) {
                holder.imageViewInOut.setImageResource(R.drawable.arrow_up_circle_outline);
            } else {
                holder.imageViewInOut.setImageResource(R.drawable.arrow_down_circle_outline);
            }

            try {
                if (rawDate != null && rawDate.length() > 0) {
                    String formattedDateString = OffsetDateTime.parse(rawDate)
                            .format(DateTimeFormatter.ofPattern("E, dd MMM yyyy HH:mm:ss"));
                    holder.textViewDate.setText(formattedDateString + " GMT");
                } else {
                    holder.textViewDate.setText("");
                }
            } catch (Exception e) {
                holder.textViewDate.setText("");
            }

            try {
                if (rawValue != null && rawValue.length() > 0) {
                    BigInteger valueBigInteger = new BigInteger(rawValue.replace("0x", ""), 16);
                    String wei = valueBigInteger.toString(10);
                    holder.textViewQuantity.setText(CoinUtils.formatWei(wei));
                } else {
                    holder.textViewQuantity.setText("0");
                }
            } catch (Exception e) {
                holder.textViewQuantity.setText("0");
            }

            holder.textViewTransHash.setText(hash.length() >= 7 ? hash.substring(0, 7) : hash);
            holder.textViewFrom.setText(from.length() >= 7 ? from.substring(0, 7) : from);
            holder.textViewTo.setText(to.length() >= 7 ? to.substring(0, 7) : to);

            if (hash.length() > 0) {
                holder.textViewTransHash.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        context.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(GlobalMethods.BLOCK_EXPLORER_URL + GlobalMethods.BLOCK_EXPLORER_TX_HASH_URL.replace("{txhash}", hash))));
                    }
                });
            }

            if (from.length() > 0) {
                holder.textViewFrom.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        context.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(GlobalMethods.BLOCK_EXPLORER_URL + GlobalMethods.BLOCK_EXPLORER_ACCOUNT_TRANSACTION_URL.replace("{address}", from))));
                    }
                });
            }

            if (to.length() > 0) {
                holder.textViewTo.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        context.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(GlobalMethods.BLOCK_EXPLORER_URL + GlobalMethods.BLOCK_EXPLORER_ACCOUNT_TRANSACTION_URL.replace("{address}", to))));
                    }
                });
            }

        } catch (Exception ex) {
            GlobalMethods.ExceptionError(context, TAG, ex);
        }
    }

    @Override
    public int getItemCount() {
        return this.accountTransactionSummaries == null ? 0 : this.accountTransactionSummaries.size();
    }
}

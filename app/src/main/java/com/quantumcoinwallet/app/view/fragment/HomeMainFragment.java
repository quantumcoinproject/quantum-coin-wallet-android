package com.quantumcoinwallet.app.view.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.api.read.ApiException;
import com.quantumcoinwallet.app.api.read.model.AccountTokenListResponse;
import com.quantumcoinwallet.app.api.read.model.AccountTokenSummary;
import com.quantumcoinwallet.app.asynctask.read.ListAccountTokensRestTask;
import com.quantumcoinwallet.app.utils.GlobalMethods;
import com.quantumcoinwallet.app.view.adapter.TokenAdapter;
import com.quantumcoinwallet.app.view.widget.VerticalScrollIndicatorView;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HomeMainFragment extends Fragment  {

    private static final String TAG = "HomeMainFragment";

    private HomeMainFragment.OnHomeMainCompleteListener mHomeMainListener;

    private RecyclerView tokenRecyclerView;
    private HorizontalScrollView tokenScrollContainer;
    private LinearLayout tokenScrollRow;
    private VerticalScrollIndicatorView tokenScrollLeft;
    private VerticalScrollIndicatorView tokenScrollRight;
    private TextView tokenEmptyTextView;
    private TextView tokenTitleTextView;
    private TextView tokenHeaderSymbol;
    private TextView tokenHeaderBalance;
    private TextView tokenHeaderContract;
    private TextView tokenHeaderName;
    private TokenAdapter tokenAdapter;

    private String languageKey;
    private String walletAddress;

    public static HomeMainFragment newInstance() {
        HomeMainFragment fragment = new HomeMainFragment();
        return fragment;
    }

    public HomeMainFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
            return inflater.inflate(R.layout.home_main_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            if (getArguments() != null) {
                languageKey = getArguments().getString("languageKey");
                walletAddress = getArguments().getString("walletAddress");
            }

            tokenRecyclerView = view.findViewById(R.id.recyclerView_tokenList);
            tokenScrollContainer = view.findViewById(R.id.horizontalScroll_tokenList);
            tokenScrollRow = view.findViewById(R.id.tokenList_scrollRow);
            tokenScrollLeft = view.findViewById(R.id.verticalScroll_tokenList_left);
            tokenScrollRight = view.findViewById(R.id.verticalScroll_tokenList_right);
            tokenEmptyTextView = view.findViewById(R.id.textView_tokenList_empty);
            tokenTitleTextView = view.findViewById(R.id.textView_tokenList_title);
            tokenHeaderSymbol = view.findViewById(R.id.textView_tokenList_header_symbol);
            tokenHeaderBalance = view.findViewById(R.id.textView_tokenList_header_balance);
            tokenHeaderContract = view.findViewById(R.id.textView_tokenList_header_contract);
            tokenHeaderName = view.findViewById(R.id.textView_tokenList_header_name);

            JsonViewModel jsonViewModel = new JsonViewModel(getContext(), languageKey);
            tokenTitleTextView.setText(jsonViewModel.getTokensByLangValues());
            tokenHeaderSymbol.setText(jsonViewModel.getSymbolByLangValues());
            tokenHeaderBalance.setText(jsonViewModel.getBalanceByLangValues());
            tokenHeaderContract.setText(jsonViewModel.getContractByLangValues());
            tokenHeaderName.setText(jsonViewModel.getNameByLangValues());
            tokenEmptyTextView.setText(jsonViewModel.getNoTokensByLangValues());

            tokenRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            tokenAdapter = new TokenAdapter(getContext(), getCachedTokensForAddress(walletAddress));
            tokenRecyclerView.setAdapter(tokenAdapter);

            if (tokenScrollLeft != null) {
                tokenScrollLeft.attachTo(tokenRecyclerView);
            }
            if (tokenScrollRight != null) {
                tokenScrollRight.attachTo(tokenRecyclerView);
            }

            renderEmptyState(tokenAdapter.getItemCount() == 0);

            refreshTokenList(walletAddress);
        } catch (Exception ex) {
            GlobalMethods.ExceptionError(getContext(), TAG, ex);
        }

        mHomeMainListener.onHomeMainComplete();
    }

    private List<AccountTokenSummary> getCachedTokensForAddress(String address) {
        if (address != null && Objects.equals(GlobalMethods.CURRENT_WALLET_TOKEN_LIST_ADDRESS, address)
                && GlobalMethods.CURRENT_WALLET_TOKEN_LIST != null) {
            return new ArrayList<>(GlobalMethods.CURRENT_WALLET_TOKEN_LIST);
        }
        return new ArrayList<>();
    }

    private void renderEmptyState(boolean empty) {
        if (tokenScrollRow != null) {
            tokenScrollRow.setVisibility(empty ? View.GONE : View.VISIBLE);
        } else if (tokenScrollContainer != null) {
            tokenScrollContainer.setVisibility(empty ? View.GONE : View.VISIBLE);
        } else if (tokenRecyclerView != null) {
            tokenRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
        // "No tokens for this address" placeholder is intentionally suppressed; an
        // empty wallet simply shows nothing beneath the Send/Receive panel.
        if (tokenEmptyTextView != null) {
            tokenEmptyTextView.setVisibility(View.GONE);
        }
        if (tokenTitleTextView != null) {
            tokenTitleTextView.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Kicks the scan API token listing. Updates the adapter + global cache on success.
     */
    public void refreshTokenList(final String address) {
        if (address == null || address.isEmpty()) {
            return;
        }
        if (!GlobalMethods.IsNetworkAvailable(getContext())) {
            return;
        }
        try {
            String[] taskParams = { address, "1" };
            ListAccountTokensRestTask task = new ListAccountTokensRestTask(
                    getContext(), new ListAccountTokensRestTask.TaskListener() {
                @Override
                public void onFinished(AccountTokenListResponse response) {
                    List<AccountTokenSummary> items = (response == null || response.getItems() == null)
                            ? new ArrayList<AccountTokenSummary>()
                            : response.getItems();
                    GlobalMethods.CURRENT_WALLET_TOKEN_LIST = new ArrayList<>(items);
                    GlobalMethods.CURRENT_WALLET_TOKEN_LIST_ADDRESS = address;
                    if (tokenAdapter != null) {
                        tokenAdapter.setTokens(items);
                        renderEmptyState(items.isEmpty());
                    }
                }

                @Override
                public void onFailure(ApiException apiException) {
                    // Silent: token listing is best-effort. Leave cached state as-is.
                    if (tokenAdapter != null) {
                        renderEmptyState(tokenAdapter.getItemCount() == 0);
                    }
                }
            });
            task.execute(taskParams);
        } catch (Exception ex) {
            GlobalMethods.ExceptionError(getContext(), TAG, ex);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop(){
        super.onStop();
    }

    public static interface OnHomeMainCompleteListener {
        public abstract void onHomeMainComplete();
    }

    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.mHomeMainListener = (HomeMainFragment.OnHomeMainCompleteListener)context;
        } catch (final ClassCastException e) {
            throw new ClassCastException(context.toString() + " ");
        }
    }

}

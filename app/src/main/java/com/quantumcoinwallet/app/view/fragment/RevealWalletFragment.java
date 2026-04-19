package com.quantumcoinwallet.app.view.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.entity.KeyServiceException;
import com.quantumcoinwallet.app.entity.ServiceException;
import com.quantumcoinwallet.app.keystorage.SecureStorage;
import com.quantumcoinwallet.app.utils.GlobalMethods;
import com.quantumcoinwallet.app.utils.PrefConnect;
import com.quantumcoinwallet.app.view.adapter.SeedWordAutoCompleteAdapter;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;
import com.quantumcoinwallet.app.viewmodel.KeyViewModel;

import org.json.JSONObject;

import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;

public class RevealWalletFragment extends Fragment {

    private static final String TAG = "RevealSeedWalletFragment";

    private boolean ThreadStop = false;
    private int revealedWordCount = 48;

    private OnRevealWalletCompleteListener mRevealWalletListener;

    public static RevealWalletFragment newInstance() {
        RevealWalletFragment fragment = new RevealWalletFragment();
        return fragment;
    }

    public RevealWalletFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.reveal_wallet_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String languageKey = getArguments().getString("languageKey");
        String walletAddress  = getArguments().getString("walletAddress");

        JsonViewModel jsonViewModel = new JsonViewModel(getContext(), languageKey);

        //LinearLayout revealSetWalletTopLinearLayout = (LinearLayout) getView().findViewById(R.id.top_linear_layout_reveal_seed_wallet_id);
        ImageButton homeWalletBackArrowImageButton = (ImageButton) getView().findViewById(R.id.imageButton_reveal_seed_wallet_back_arrow);

        //LinearLayout revealSeedWordsViewLinearLayout = (LinearLayout) getView().findViewById(R.id.linear_layout_reveal_seed_words_view);
        TextView revealSeedWordsViewTitleTextView = (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_title);
        TextView[] revealSeedWordsViewCaptionTextViews = RevealSeedWordsViewCaptionTextViews();
        TextView[] revealSeedWordsViewTextViews = RevealSeedWordsViewTextViews();

        ProgressBar progressBar = (ProgressBar) getView().findViewById(R.id.progress_loader_reveal_seed_wallet);

        ImageButton revealSeedWordsViewCopyClipboardImageButton = (ImageButton) getView().findViewById(R.id.imageButton_reveal_seed_words_view_copy_clipboard);
        TextView revealSeedWordsViewCopyLink = (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_copy_link);
        TextView revealSeedWordsViewCopied = (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_copied);

        revealSeedWordsViewCopyLink.setText(jsonViewModel.getCopyByLangValues());
        revealSeedWordsViewCopied.setText(jsonViewModel.getCopiedByLangValues());

        try {
            ShowRevealSeedScreen(jsonViewModel,
                    revealSeedWordsViewTitleTextView, revealSeedWordsViewCaptionTextViews, revealSeedWordsViewTextViews, progressBar, walletAddress);

        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            GlobalMethods.ShowErrorDialog(getContext(), "Error", e.getMessage());
        }
        homeWalletBackArrowImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    ThreadStop = true;
                    Thread.sleep(1000);
                    mRevealWalletListener.onRevealWalletComplete();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            public void run() {
                                GlobalMethods.ExceptionError(getContext(), TAG, e);
                            }
                        });
                    }
                }
            }
        });

        View.OnClickListener revealCopyClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                progressBar.setVisibility(View.VISIBLE);
                String clipboardCopyData = ClipboardCopyData(revealSeedWordsViewCaptionTextViews, revealSeedWordsViewTextViews);
                ClipboardManager clipBoard = (ClipboardManager) getActivity().getSystemService(getActivity().CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("walletrevealSeed", clipboardCopyData);
                clipBoard.setPrimaryClip(clipData);
                progressBar.setVisibility(View.GONE);
                revealSeedWordsViewCopied.setVisibility(View.VISIBLE);
                new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        revealSeedWordsViewCopied.setVisibility(View.GONE);
                    }
                }, 600);
            }
        };
        revealSeedWordsViewCopyClipboardImageButton.setOnClickListener(revealCopyClickListener);
        revealSeedWordsViewCopyLink.setOnClickListener(revealCopyClickListener);

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    public static interface OnRevealWalletCompleteListener {
        public abstract void onRevealWalletComplete();
    }

    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.mRevealWalletListener = (OnRevealWalletCompleteListener) context;
        } catch (final ClassCastException e) {
            throw new ClassCastException(context.toString() + " ");
        }
    }

    private void ShowRevealSeedScreen(JsonViewModel jsonViewModel,
                                     TextView revealSeedWordsViewTitleTextView, TextView[] captionViews, TextView[] textViews,
                                   ProgressBar progressBar, String walletAddress) throws Exception {

        revealSeedWordsViewTitleTextView.setText(jsonViewModel.getSeedWordsByLangValues());

        SecureStorage secureStorage = KeyViewModel.getSecureStorage();
        String indexStr = PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.get(walletAddress);
        if (indexStr == null) {
            throw new Exception("Wallet not found for address");
        }
        String walletJsonStr = secureStorage.loadWallet(getContext(), Integer.parseInt(indexStr));
        JSONObject walletData = new JSONObject(walletJsonStr);
        String seedStr = walletData.getString("seed");

        String[] seedWordsList = seedStr.split(",");
        revealedWordCount = seedWordsList.length;
        loadRevealSeedWords(seedWordsList, textViews, progressBar);
        updateRevealRowVisibility(captionViews, textViews, revealedWordCount);
    }

    private void loadRevealSeedWords(String[] wordList, TextView[] textViews, ProgressBar progressBar) {
        progressBar.setVisibility(View.VISIBLE);
        try {
            for (int i = 0; i < wordList.length && i < textViews.length; i++) {
                textViews[i].setText(wordList[i].trim().toUpperCase());
            }
            for (int i = wordList.length; i < textViews.length; i++) {
                textViews[i].setText("");
            }
        } catch (Exception e) {
            GlobalMethods.ShowErrorDialog(getContext(), "Error", e.getMessage());
        }
        progressBar.setVisibility(View.GONE);
    }

    private void updateRevealRowVisibility(TextView[] captions, TextView[] values, int wordCount) {
        int totalRows = wordCount / 4;
        for (int row = 0; row < 12; row++) {
            int visibility = (row < totalRows) ? View.VISIBLE : View.GONE;
            int idx = row * 4;
            if (idx < captions.length) {
                ((View) captions[idx].getParent()).setVisibility(visibility);
            }
            if (idx < values.length) {
                ((View) values[idx].getParent()).setVisibility(visibility);
            }
        }
    }

    private TextView[] RevealSeedWordsViewCaptionTextViews() {
        TextView[] revealSeedWordsViewCaptionTextViews = new TextView[]{
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_a1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_a2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_a3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_a4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_b1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_b2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_b3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_b4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_c1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_c2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_c3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_c4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_d1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_d2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_d3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_d4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_e1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_e2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_e3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_e4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_f1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_f2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_f3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_f4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_g1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_g2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_g3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_g4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_h1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_h2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_h3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_h4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_i1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_i2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_i3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_i4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_j1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_j2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_j3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_j4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_k1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_k2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_k3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_k4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_l1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_l2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_l3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_caption_l4),

        };
        return revealSeedWordsViewCaptionTextViews;
    }

    private TextView[]RevealSeedWordsViewTextViews() {
        TextView[] revealSeedWordsViewTextViews = new TextView[]{
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_a1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_a2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_a3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_a4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_b1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_b2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_b3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_b4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_c1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_c2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_c3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_c4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_d1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_d2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_d3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_d4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_e1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_e2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_e3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_e4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_f1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_f2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_f3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_f4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_g1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_g2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_g3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_g4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_h1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_h2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_h3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_h4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_i1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_i2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_i3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_i4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_j1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_j2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_j3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_j4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_k1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_k2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_k3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_k4),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_l1),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_l2),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_l3),
                (TextView) getView().findViewById(R.id.textView_reveal_seed_words_view_l4),

        };
        return revealSeedWordsViewTextViews;
    }

    private String ClipboardCopyData(TextView[] revealSeedWordsViewCaptionTextViews, TextView[] revealSeedWordsViewTextViews){
        String copyData = "";
        int limit = Math.min(revealedWordCount, revealSeedWordsViewCaptionTextViews.length);
        for (int i=0; i<limit; i++) {
            copyData = copyData + revealSeedWordsViewCaptionTextViews[i].getText() + " = " +  revealSeedWordsViewTextViews[i].getText() + "\n";
        }
        return copyData;
    }

}
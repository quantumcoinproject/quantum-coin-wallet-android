package com.quantumcoinwallet.app.view.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.utils.PrefConnect;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;

public class SettingsFragment extends Fragment  {

    private static final String TAG = "SettingsFragment";

    private LinearLayout linerLayoutOffline;
    private ImageView imageViewRetry;
    private TextView textViewTitleRetry;
    private TextView textViewSubTitleRetry;


    private OnSettingsCompleteListener mSettingsListener;

    public static SettingsFragment newInstance() {
        SettingsFragment fragment = new SettingsFragment();
        return fragment;
    }

    public SettingsFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.settings_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String languageKey = getArguments().getString("languageKey");

        JsonViewModel jsonViewModel = new JsonViewModel(getContext(), languageKey);

        ImageButton backArrowImageButton = (ImageButton) getView().findViewById(R.id.imageButton_setting_back_arrow);
        TextView settings = (TextView) getView().findViewById(R.id.textview_settings_langValues_settings);
        Button buttonNetworks = (Button) getView().findViewById(R.id.button_settings_langValues_networks);
        Button buttonSigning = (Button) getView().findViewById(R.id.button_settings_langValues_signing);

        settings.setText(jsonViewModel.getSettingsByLangValues());
        buttonNetworks.setText(jsonViewModel.getNetworksByLangValues());
        buttonSigning.setText(jsonViewModel.getSigningByLangValues());

        linerLayoutOffline = (LinearLayout) getView().findViewById(R.id.linerLayout_setting_offline);
        imageViewRetry = (ImageView) getView().findViewById(R.id.image_retry);
        textViewTitleRetry = (TextView) getView().findViewById(R.id.textview_title_retry);
        textViewSubTitleRetry = (TextView) getView().findViewById(R.id.textview_subtitle_retry);
        Button buttonRetry = (Button) getView().findViewById(R.id.button_retry);

        backArrowImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mSettingsListener.onSettingsCompleteCompleteByBackArrow();
            }
        });

        buttonNetworks.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                mSettingsListener.onSettingsCompleteByNetwork();
            }
        });

        buttonSigning.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                showAdvancedSigningDialog(jsonViewModel);
            }
        });

        buttonRetry.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                mSettingsListener.onSettingsCompleteCompleteByBackArrow();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop(){
        super.onStop();
    }

    private void showAdvancedSigningDialog(JsonViewModel jsonViewModel) {
        boolean currentValue = PrefConnect.readBoolean(getContext(), PrefConnect.ADVANCED_SIGNING_ENABLED_KEY, false);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);

        TextView description = new TextView(getContext());
        description.setText(jsonViewModel.getAdvancedSigningDescriptionByLangValues());
        description.setPadding(0, 0, 0, 24);
        layout.addView(description);

        RadioGroup radioGroup = new RadioGroup(getContext());
        radioGroup.setOrientation(RadioGroup.VERTICAL);

        RadioButton radioEnabled = new RadioButton(getContext());
        radioEnabled.setId(View.generateViewId());
        radioEnabled.setText(jsonViewModel.getAdvancedSigningOptionByLangValues());
        radioGroup.addView(radioEnabled);

        RadioButton radioDisabled = new RadioButton(getContext());
        radioDisabled.setId(View.generateViewId());
        radioDisabled.setText(jsonViewModel.getDisabledByLangValues());
        radioGroup.addView(radioDisabled);

        if (currentValue) {
            radioEnabled.setChecked(true);
        } else {
            radioDisabled.setChecked(true);
        }

        layout.addView(radioGroup);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(jsonViewModel.getSigningByLangValues());
        builder.setView(layout);
        builder.setPositiveButton(jsonViewModel.getOkByLangValues(), (dialog, which) -> {
            boolean enabled = radioEnabled.isChecked();
            PrefConnect.writeBoolean(getContext(), PrefConnect.ADVANCED_SIGNING_ENABLED_KEY, enabled);
            dialog.dismiss();
        });
        builder.setNegativeButton(jsonViewModel.getCancelByLangValues(), (dialog, which) -> {
            dialog.dismiss();
        });
        builder.show();
    }

    public static interface OnSettingsCompleteListener {
        public abstract void onSettingsCompleteCompleteByBackArrow();
        public abstract void onSettingsCompleteByNetwork();
    }

    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.mSettingsListener = (OnSettingsCompleteListener)context;
        }
        catch (final ClassCastException e) {
            throw new ClassCastException(context.toString() + " ");
        }
    }
}

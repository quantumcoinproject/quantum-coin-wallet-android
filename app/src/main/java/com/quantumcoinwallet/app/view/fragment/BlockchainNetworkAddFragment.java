package com.quantumcoinwallet.app.view.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.fragment.app.Fragment;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.model.BlockchainNetwork;
import com.quantumcoinwallet.app.utils.GlobalMethods;
import com.quantumcoinwallet.app.utils.PrefConnect;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.regex.Pattern;

import timber.log.Timber;


public class BlockchainNetworkAddFragment extends Fragment  {

    private static final String TAG = "BlockchainNetworkAddFragment";

    private static final Pattern HOSTNAME_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)(\\.([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?))+$");
    private static final Pattern NETWORK_ID_PATTERN = Pattern.compile("^\\d{1,18}$");
    private static final int BLOCKCHAIN_NAME_MAX_LEN = 64;

    private static boolean isValidHostname(String host) {
        return host != null && HOSTNAME_PATTERN.matcher(host).matches();
    }

    private static boolean isValidBlockchainName(String name) {
        if (name == null) return false;
        if (name.length() == 0 || name.length() > BLOCKCHAIN_NAME_MAX_LEN) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ')) return false;
        }
        return true;
    }

    private BlockchainNetworkAddFragment.OnBlockchainNetworkAddCompleteListener mBlockchainNetworkAddListener;

    public static BlockchainNetworkAddFragment newInstance() {
        BlockchainNetworkAddFragment fragment = new BlockchainNetworkAddFragment();
        return fragment;
    }

    public BlockchainNetworkAddFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.blockchain_network_add_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            assert getArguments() != null;

            String languageKey = getArguments().getString("languageKey");

            JsonViewModel jsonViewModel = new JsonViewModel(getContext(), languageKey);

            ImageButton backArrowImageButton = (ImageButton) getView().findViewById(R.id.imageButton_blockchain_network_add_back_arrow);

            TextView blockchainNetworkAddNetworkTextView = (TextView) getView().findViewById(R.id.textview_blockchain_network_add_langValues_add_network);
            TextView blockchainNetworkEnterNetworkJsonTextView = (TextView) getView().findViewById(R.id.textview_blockchain_network_add_langValues_enter_network_json);
            EditText blockchainNetworkAddNetworkEditText = (EditText) getView().findViewById(R.id.editText_blockchain_network_add_langValues_add_network);
            blockchainNetworkAddNetworkEditText.setHorizontallyScrolling(true);

            Button blockchainNetworkAddNetworkButton = (Button) getView().findViewById(R.id.button_blockchain_network_add_langValues_add);

            ProgressBar progressBar = (ProgressBar) getView().findViewById(R.id.progress_blockchain_network_add);

            try {
                blockchainNetworkAddNetworkEditText.setText(makeJSON().toString(2).replace("\\/", "/"));
            } catch (Exception e) {
                blockchainNetworkAddNetworkEditText.setText(makeJSON().toString().replace("\\/", "/"));
            }

            blockchainNetworkAddNetworkTextView.setText(jsonViewModel.getAddNetworkByLangValues());
            blockchainNetworkEnterNetworkJsonTextView.setText(jsonViewModel.getEnterNetworkJsonByLangValues());
            blockchainNetworkAddNetworkButton.setText(jsonViewModel.getAddByLangValues());

            backArrowImageButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mBlockchainNetworkAddListener.onBlockchainNetworkAddComplete();
                }
            });

            blockchainNetworkAddNetworkButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    progressBar.setVisibility(View.VISIBLE);
                    JSONObject obj;
                    try {
                        obj = new JSONObject(blockchainNetworkAddNetworkEditText.getText().toString());
                    } catch (org.json.JSONException je) {
                        progressBar.setVisibility(View.GONE);
                        String invalidMsg = jsonViewModel.getInvalidNetworkJsonByErrors();
                        if (invalidMsg == null || invalidMsg.isEmpty()) {
                            invalidMsg = "The JSON is invalid.";
                        }
                        GlobalMethods.ShowErrorDialog(getContext(),
                                jsonViewModel.getErrorTitleByLangValues(), invalidMsg);
                        return;
                    }
                    try {
                        String scanApiDomain = obj.optString("scanApiDomain", "").trim();
                        String rpcEndpoint = obj.optString("rpcEndpoint", "").trim();
                        String blockExplorerDomain = obj.optString("blockExplorerDomain", "").trim();
                        String blockchainName = obj.optString("blockchainName", "").trim();
                        String networkId = String.valueOf(obj.opt("networkId")).trim();

                        final String errorTitle = jsonViewModel.getErrorTitleByLangValues();

                        if (!rpcEndpoint.startsWith("https://")) {
                            GlobalMethods.ShowErrorDialog(getContext(), errorTitle,
                                    "RPC Endpoint must use https://");
                            progressBar.setVisibility(View.GONE);
                            return;
                        }
                        try {
                            java.net.URL u = new java.net.URL(rpcEndpoint);
                            if (!"https".equalsIgnoreCase(u.getProtocol())
                                    || !isValidHostname(u.getHost())) {
                                throw new java.net.MalformedURLException("bad host");
                            }
                        } catch (java.net.MalformedURLException mu) {
                            GlobalMethods.ShowErrorDialog(getContext(), errorTitle,
                                    "RPC Endpoint URL is not a valid https host.");
                            progressBar.setVisibility(View.GONE);
                            return;
                        }
                        if (!isValidHostname(scanApiDomain)) {
                            GlobalMethods.ShowErrorDialog(getContext(), errorTitle,
                                    "Scan API domain is not a valid hostname.");
                            progressBar.setVisibility(View.GONE);
                            return;
                        }
                        if (!isValidHostname(blockExplorerDomain)) {
                            GlobalMethods.ShowErrorDialog(getContext(), errorTitle,
                                    "Block explorer domain is not a valid hostname.");
                            progressBar.setVisibility(View.GONE);
                            return;
                        }
                        if (!isValidBlockchainName(blockchainName)) {
                            GlobalMethods.ShowErrorDialog(getContext(), errorTitle,
                                    "Blockchain name must be 1-" + BLOCKCHAIN_NAME_MAX_LEN
                                            + " letters/digits/_/-/space.");
                            progressBar.setVisibility(View.GONE);
                            return;
                        }
                        if (!NETWORK_ID_PATTERN.matcher(networkId).matches()) {
                            GlobalMethods.ShowErrorDialog(getContext(), errorTitle,
                                    "Network ID must be a positive integer.");
                            progressBar.setVisibility(View.GONE);
                            return;
                        }

                        List<BlockchainNetwork> blockchainNetworkList = GlobalMethods.BlockChainNetworkRead(getContext());
                        BlockchainNetwork blockchainNetwork = new BlockchainNetwork();
                        blockchainNetwork.setScanApiDomain(scanApiDomain);
                        blockchainNetwork.setRpcEndpoint(rpcEndpoint);
                        blockchainNetwork.setBlockExplorerDomain(blockExplorerDomain);
                        blockchainNetwork.setBlockchainName(blockchainName);
                        blockchainNetwork.setNetworkId(networkId);
                        blockchainNetworkList.add(blockchainNetwork);

                        JSONArray networksArray = new JSONArray();
                        for (BlockchainNetwork n : blockchainNetworkList) {
                            JSONObject entry = new JSONObject();
                            entry.put("scanApiDomain", n.getScanApiDomain());
                            entry.put("rpcEndpoint", n.getRpcEndpoint());
                            entry.put("blockExplorerDomain", n.getBlockExplorerDomain());
                            entry.put("blockchainName", n.getBlockchainName());
                            try {
                                entry.put("networkId", Long.parseLong(n.getNetworkId()));
                            } catch (NumberFormatException nfe) {
                                entry.put("networkId", n.getNetworkId());
                            }
                            networksArray.put(entry);
                        }
                        JSONObject root = new JSONObject();
                        root.put("networks", networksArray);
                        PrefConnect.writeString(getContext(), PrefConnect.BLOCKCHAIN_NETWORK_LIST,
                                root.toString());

                        GlobalMethods.ShowMessageDialog(getContext(), null,
                                "Added successfully!",
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mBlockchainNetworkAddListener != null) {
                                            mBlockchainNetworkAddListener.onBlockchainNetworkAddComplete();
                                        }
                                    }
                                });
                    } catch (Exception e) {
                        GlobalMethods.ExceptionError(getContext(), TAG, e);
                    }
                    progressBar.setVisibility(View.GONE);
                }
            });
        } catch(Exception e){
            GlobalMethods.ExceptionError(getContext(), TAG, e);
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

    public static interface OnBlockchainNetworkAddCompleteListener {
        public abstract void onBlockchainNetworkAddComplete();
    }

    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.mBlockchainNetworkAddListener = (BlockchainNetworkAddFragment.OnBlockchainNetworkAddCompleteListener)context;
        }
        catch (final ClassCastException e) {
            throw new ClassCastException(context.toString() + " ");
        }
    }

    public JSONObject makeJSON() {
        JSONObject jObj = new JSONObject();
        try {
            jObj.put("scanApiDomain", "app.readrelay.quantumcoinapi.com");
            jObj.put("rpcEndpoint",  "https://public.rpc.quantumcoinapi.com");
            jObj.put("blockExplorerDomain",  "quantumscan.com");
            jObj.put("blockchainName",  "MAINNET");
            jObj.put("networkId",  123123);
        } catch (Exception e) {
            Timber.w(e, "makeJSON failed");
        }
        return jObj;
    }

}
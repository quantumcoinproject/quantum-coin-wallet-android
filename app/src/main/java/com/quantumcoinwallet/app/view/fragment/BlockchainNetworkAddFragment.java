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
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.model.BlockchainNetwork;
import com.quantumcoinwallet.app.utils.GlobalMethods;
import com.quantumcoinwallet.app.utils.PrefConnect;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;
import org.json.JSONObject;
import java.util.List;


public class BlockchainNetworkAddFragment extends Fragment  {

    private static final String TAG = "BlockchainNetworkAddFragment";

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
                        String scanApiDomain = (String) obj.get("scanApiDomain");
                        String rpcEndpoint = (String) obj.get("rpcEndpoint");
                        String blockExplorerDomain = (String) obj.get("blockExplorerDomain");
                        String blockchainName = (String) obj.get("blockchainName");
                        String networkId = String.valueOf(obj.get("networkId"));

                        if (!rpcEndpoint.startsWith("http://") && !rpcEndpoint.startsWith("https://")) {
                            GlobalMethods.ShowErrorDialog(getContext(),
                                    jsonViewModel.getErrorTitleByLangValues(),
                                    "RPC Endpoint must start with http:// or https://");
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

                        String jsonString = "";

                        for(BlockchainNetwork blockchainNetwork1 :  blockchainNetworkList ){
                            if(jsonString != "")
                            {
                                jsonString = jsonString + ",";
                            }
                            jsonString = jsonString + "{'scanApiDomain': '" +  blockchainNetwork1.getScanApiDomain() + "'," +
                                    "'rpcEndpoint': '" +  blockchainNetwork1.getRpcEndpoint() + "'," +
                                    "'blockExplorerDomain': '" +  blockchainNetwork1.getBlockExplorerDomain() + "'," +
                                    "'blockchainName': '" +  blockchainNetwork1.getBlockchainName() + "'," +
                                    "'networkId': " +  blockchainNetwork1.getNetworkId() + "}";
                        }

                        String json = "{ 'networks' : [" + jsonString+ "]}";
                        PrefConnect.writeString(getContext(), PrefConnect.BLOCKCHAIN_NETWORK_LIST,json);

                        Toast.makeText(getContext(), "Added successfully!",
                                Toast.LENGTH_SHORT).show();

                        mBlockchainNetworkAddListener.onBlockchainNetworkAddComplete();
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
            System.out.println("Error:" + e);
        }
        return jObj;
    }

}
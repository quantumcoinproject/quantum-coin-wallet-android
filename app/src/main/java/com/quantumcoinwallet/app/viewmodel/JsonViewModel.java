package com.quantumcoinwallet.app.viewmodel;

import android.content.Context;

import androidx.lifecycle.ViewModel;

import com.quantumcoinwallet.app.interact.JsonInteract;
import com.quantumcoinwallet.app.utils.GlobalMethods;

import org.json.JSONException;

import java.util.ArrayList;

import timber.log.Timber;

public class JsonViewModel extends ViewModel{
    private JsonInteract _jsonInteract;

    public JsonViewModel(Context context, String languageKey) {
        try {
            String jsonString = GlobalMethods.LocaleLanguage(context,  languageKey);
            _jsonInteract = new JsonInteract(jsonString);
        } catch (JSONException e) {
            Timber.w(e, "lang key lookup failed");
        }
    }
    public String getInfoStep() {
        try { return _jsonInteract.getInfoStep(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public int getInfoLength() {
        try { return _jsonInteract.getInfoLength(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return 0;
    }
    public String getTitleByInfo(int index) {
        try { return _jsonInteract.getTitleByInfo(index); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getDescByInfo(int index) {
        try { return _jsonInteract.getDescByInfo(index); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getQuizStep() {
        try { return _jsonInteract.getQuizStep(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getQuizWrongAnswer(){
        try { return _jsonInteract.getQuizWrongAnswer(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getQuizNoChoice(){
        try { return _jsonInteract.getQuizNoChoice(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public int getQuizLength() {
        try { return _jsonInteract.getQuizLength(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return 0;
    }
    public String getTitleByQuiz(int index) {
        try { return _jsonInteract.getTitleByQuiz(index); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getQuestionByQuiz(int index) {
        try { return _jsonInteract.getQuestionByQuiz(index); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public ArrayList<String> getChoicesByQuiz(int index) {
        try { return _jsonInteract.getChoicesByQuiz(index); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public int getCorrectChoiceByQuiz(int index){
        try { return _jsonInteract.getCorrectChoiceByQuiz(index); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return -1;
    }
    public String getAfterQuizInfoByQuiz(int index){
        try { return _jsonInteract.getAfterQuizInfoByQuiz(index); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }

    public String getTitleByLangValues(){
        try { return _jsonInteract.getTitleByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getNextByLangValues() {
        try { return _jsonInteract.getNextByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getOkByLangValues() {
        try { return _jsonInteract.getOkByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getCancelByLangValues() {
        try { return _jsonInteract.getCancelByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getCloseByLangValues() {
        try { return _jsonInteract.getCloseByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSendByLangValues() {
        try { return _jsonInteract.getSendByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getReceiveByLangValues() {
        try { return _jsonInteract.getReceiveByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getTransactionsByLangValues() {
        try { return _jsonInteract.getTransactionsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getCopyByLangValues() {
        try { return _jsonInteract.getCopyByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBalanceByLangValues() {
        try { return _jsonInteract.getBalanceByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getCompletedTransactionsByLangValues() {
        try { return _jsonInteract.getCompletedTransactionsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getPendingTransactionsByLangValues() {
        try { return _jsonInteract.getPendingTransactionsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getWalletsByLangValues() {
        try { return _jsonInteract.getWalletsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSettingsByLangValues() {
        try { return _jsonInteract.getSettingsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getUnlockByLangValues() {
        try { return _jsonInteract.getUnlockByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getUnlockWalletByLangValues() {
        try { return _jsonInteract.getUnlockWalletByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSelectNetworkByLangValues() {
        try { return _jsonInteract.getSelectNetworkByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getEnterApasswordByLangValues() {
        try { return _jsonInteract.getEnterApasswordByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getPasswordByLangValues() {
        try { return _jsonInteract.getPasswordByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSetWalletPasswordByLangValues() {
        try { return _jsonInteract.getSetWalletPasswordByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getUseStrongPasswordByLangValues() {
        try { return _jsonInteract.getUseStrongPasswordByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRetypePasswordByLangValues() {
        try { return _jsonInteract.getRetypePasswordByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRetypeThePasswordByLangValues() {
        try { return _jsonInteract.getRetypeThePasswordByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getCreateRestoreWalletByLangValues() {
        try { return _jsonInteract.getCreateRestoreWalletByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSelectAnOptionByLangValues() {
        try { return _jsonInteract.getSelectAnOptionByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getCreateNewWalletByLangValues() {
        try { return _jsonInteract.getCreateNewWalletByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreWalletFromSeedByLangValues() {
        try { return _jsonInteract.getRestoreWalletFromSeedByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSeedWordsByLangValues() {
        try { return _jsonInteract.getSeedWordsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSeedWordsInfo1ByLangValues() {
        try { return _jsonInteract.getSeedWordsInfo1ByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSeedWordsInfo2ByLangValues() {
        try { return _jsonInteract.getSeedWordsInfo2ByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSeedWordsInfo3ByLangValues() {
        try { return _jsonInteract.getSeedWordsInfo3ByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSeedWordsInfo4ByLangValues() {
        try { return _jsonInteract.getSeedWordsInfo4ByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSeedWordsShowByLangValues() {
        try { return _jsonInteract.getSeedWordsShowByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getVerifySeedWordsByLangValues() {
        try { return _jsonInteract.getVerifySeedWordsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getEnterSeedWordsByLangValues() {
        try { return _jsonInteract.getEnterSeedWordsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getWaitWalletSaveByLangValues() {
        try { return _jsonInteract.getWaitWalletSaveByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getWaitWalletOpenByLangValues() {
        try { return _jsonInteract.getWaitWalletOpenByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getWaitUnlockByLangValues() {
        try { return _jsonInteract.getWaitUnlockByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getDpscanByLangValues() {
        try { return _jsonInteract.getDpscanByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getAddressByLangValues() {
        try { return _jsonInteract.getAddressByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getCoinsByLangValues() {
        try { return _jsonInteract.getCoinsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRevealSeedByLangValues() {
        try { return _jsonInteract.getRevealSeedByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getNetworksByLangValues() {
        try { return _jsonInteract.getNetworksByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getIdByLangValues() {
        try { return _jsonInteract.getIdByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getNameByLangValues() {
        try { return _jsonInteract.getNameByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getScanApiUrlByLangValues() {
        try { return _jsonInteract.getScanApiUrlByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRpcEndpointByLangValues() {
        try { return _jsonInteract.getRpcEndpointByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBlockExplorerUrlByLangValues() {
        try { return _jsonInteract.getBlockExplorerUrlByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getAddNetworkByLangValues() {
        try { return _jsonInteract.getAddNetworkByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getAddByLangValues() {
        try { return _jsonInteract.getAddByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getEnterNetworkJsonByLangValues() {
        try { return _jsonInteract.getEnterNetworkJsonByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getNetworkByLangValues() {
        try { return _jsonInteract.getNetworkByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getEnterQuantumWalletPasswordByLangValues() {
        try { return _jsonInteract.getEnterQuantumWalletPasswordByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getAddressToSendByLangValues() {
        try { return _jsonInteract.getAddressToSendByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getQuantityToSendByLangValues() {
        try { return _jsonInteract.getQuantityToSendByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getReceive_coinsByLangValues() {
        try { return _jsonInteract.getReceive_coinsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSendOnlyByLangValues() {
        try { return _jsonInteract.getSendOnlyByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getInoutByLangValues() {
        try { return _jsonInteract.getInoutByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getNoMoreTransactionsByLangValues() {
        try { return _jsonInteract.getNoMoreTransactionsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getFromByLangValues() {
        try { return _jsonInteract.getFromByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getToByLangValues() {
        try { return _jsonInteract.getToByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getHashByLangValues() {
        try { return _jsonInteract.getHashByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSelectWalletTypeByLangValues() {
        try { return _jsonInteract.getSelectWalletTypeByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getWalletTypeDefaultByLangValues() {
        try { return _jsonInteract.getWalletTypeDefaultByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getWalletTypeAdvancedByLangValues() {
        try { return _jsonInteract.getWalletTypeAdvancedByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSelectSeedWordLengthByLangValues() {
        try { return _jsonInteract.getSelectSeedWordLengthByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSeedLength32ByLangValues() {
        try { return _jsonInteract.getSeedLength32ByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSeedLength36ByLangValues() {
        try { return _jsonInteract.getSeedLength36ByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSeedLength48ByLangValues() {
        try { return _jsonInteract.getSeedLength48ByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getCopiedByLangValues() {
        try { return _jsonInteract.getCopiedByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSkipByLangValues() {
        try { return _jsonInteract.getSkipByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSkipVerifyConfirmByLangValues() {
        try { return _jsonInteract.getSkipVerifyConfirmByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getYesByLangValues() {
        try { return _jsonInteract.getYesByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getNoByLangValues() {
        try { return _jsonInteract.getNoByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getErrorOccurredByLangValues() {
        try { return _jsonInteract.getErrorOccurredByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getErrorTitleByLangValues() {
        try { return _jsonInteract.getErrorTitleByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSigningByLangValues() {
        try { return _jsonInteract.getSigningByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getAdvancedSigningOptionByLangValues() {
        try { return _jsonInteract.getAdvancedSigningOptionByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getAdvancedSigningDescriptionByLangValues() {
        try { return _jsonInteract.getAdvancedSigningDescriptionByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getEnabledByLangValues() {
        try { return _jsonInteract.getEnabledByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getDisabledByLangValues() {
        try { return _jsonInteract.getDisabledByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackupByLangValues() {
        try { return _jsonInteract.getBackupByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackupPromptByLangValues() {
        try { return _jsonInteract.getBackupPromptByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackupDescriptionByLangValues() {
        try { return _jsonInteract.getBackupDescriptionByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getPhoneBackupByLangValues() {
        try { return _jsonInteract.getPhoneBackupByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackupSavedByLangValues() {
        try { return _jsonInteract.getBackupSavedByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackupFailedByLangValues() {
        try { return _jsonInteract.getBackupFailedByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackupPasswordByLangValues() {
        try { return _jsonInteract.getBackupPasswordByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getConfirmBackupPasswordByLangValues() {
        try { return _jsonInteract.getConfirmBackupPasswordByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreFromCloudByLangValues() {
        try { return _jsonInteract.getRestoreFromCloudByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreFromFileByLangValues() {
        try { return _jsonInteract.getRestoreFromFileByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackupToCloudByLangValues() {
        try { return _jsonInteract.getBackupToCloudByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getCloudBackupInfoByLangValues() {
        try { return _jsonInteract.getCloudBackupInfoByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackupToFileByLangValues() {
        try { return _jsonInteract.getBackupToFileByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackupDoneByLangValues() {
        try { return _jsonInteract.getBackupDoneByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackupSavedShortByLangValues() {
        try { return _jsonInteract.getBackupSavedShortByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackupOptionsTitleByLangValues() {
        try { return _jsonInteract.getBackupOptionsTitleByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackupOptionsDescriptionByLangValues() {
        try { return _jsonInteract.getBackupOptionsDescriptionByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getEnterBackupPasswordTitleByLangValues() {
        try { return _jsonInteract.getEnterBackupPasswordTitleByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getWalletAlreadyExistsDetailedByLangValues() {
        try { return _jsonInteract.getWalletAlreadyExistsDetailedByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getNoTransactionsByLangValues() {
        try { return _jsonInteract.getNoTransactionsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreDecryptFailedByLangValues() {
        try { return _jsonInteract.getRestoreDecryptFailedByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreEnterDifferentPasswordByLangValues() {
        try { return _jsonInteract.getRestoreEnterDifferentPasswordByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreNoBackupsFoundByLangValues() {
        try { return _jsonInteract.getRestoreNoBackupsFoundByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestorePasswordPromptRemainingByLangValues() {
        try { return _jsonInteract.getRestorePasswordPromptRemainingByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreSummaryStatusColumnByLangValues() {
        try { return _jsonInteract.getRestoreSummaryStatusColumnByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreSummaryAddressColumnByLangValues() {
        try { return _jsonInteract.getRestoreSummaryAddressColumnByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreSummaryStatusRestoredByLangValues() {
        try { return _jsonInteract.getRestoreSummaryStatusRestoredByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreSummaryStatusSkippedByLangValues() {
        try { return _jsonInteract.getRestoreSummaryStatusSkippedByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreSummaryStatusAlreadyExistsByLangValues() {
        try { return _jsonInteract.getRestoreSummaryStatusAlreadyExistsByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreTryDifferentPasswordByLangValues() {
        try { return _jsonInteract.getRestoreTryDifferentPasswordByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestoreProgressOfByLangValues() {
        try { return _jsonInteract.getRestoreProgressOfByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRestorePartialProgressByLangValues() {
        try { return _jsonInteract.getRestorePartialProgressByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getCameraPermissionDeniedByLangValues() {
        try { return _jsonInteract.getCameraPermissionDeniedByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }

    public String getTokensByLangValues() {
        try { return _jsonInteract.getTokensByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getNoTokensByLangValues() {
        try { return _jsonInteract.getNoTokensByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getContractByLangValues() {
        try { return _jsonInteract.getContractByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getSymbolByLangValues() {
        try { return _jsonInteract.getSymbolByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getAssetToSendByLangValues() {
        try { return _jsonInteract.getAssetToSendByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getBackByLangValues() {
        try { return _jsonInteract.getBackByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getConfirmWalletByLangValues() {
        try { return _jsonInteract.getConfirmWalletByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getConfirmWalletDescriptionByLangValues() {
        try { return _jsonInteract.getConfirmWalletDescriptionByLangValues(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }

    public String getSelectOptionByErrors() {
        try { return _jsonInteract.getSelectOptionByErrors(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getRetypePasswordMismatchByErrors() {
        try { return _jsonInteract.getRetypePasswordMismatchByErrors(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getPasswordSpecByErrors() {
        try { return _jsonInteract.getPasswordSpecByErrors(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getPasswordSpaceByErrors() {
        try { return _jsonInteract.getPasswordSpaceByErrors(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getWalletPasswordMismatchByErrors() {
        try { return _jsonInteract.getWalletPasswordMismatchByErrors(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getInvalidNetworkJsonByErrors() {
        try { return _jsonInteract.getInvalidNetworkJsonByErrors(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getEnterAmount() {
        try { return _jsonInteract.getEnterAmount(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getQuantumAddr() {
        try { return _jsonInteract.getQuantumAddr(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }
    public String getWalletPasswordNotSetByErrors() {
        try { return _jsonInteract.getWalletPasswordNotSetByErrors(); } catch (JSONException e) { Timber.w(e, "lang key lookup failed"); } return null;
    }

}

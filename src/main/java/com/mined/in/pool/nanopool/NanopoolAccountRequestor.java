package com.mined.in.pool.nanopool;

import static com.mined.in.http.ErrorCode.API_ERROR;
import static com.mined.in.http.ErrorCode.HTTP_ERROR;
import static com.mined.in.http.ErrorCode.JSON_ERROR;

import java.io.IOException;
import java.math.BigDecimal;

import org.json.JSONException;
import org.json.JSONObject;

import com.mined.in.pool.Account;
import com.mined.in.pool.AccountRequestor;
import com.mined.in.pool.AccountRequestorException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Nanopool account requestor.
 *
 * @author Dmitry Tverdokhleb
 *
 */
public class NanopoolAccountRequestor implements AccountRequestor {

    /** HTTP client. */
    private final OkHttpClient httpClient;
    /** API balance url. */
    private static final String API_BALANCE_URL = "https://api.nanopool.org/v1/eth/balance/";
    /** API reported hashrate url. */
    private static final String API_HASHRATE_URL = "https://api.nanopool.org/v1/eth/reportedhashrate/";

    /**
     * Creates the instance.
     *
     * @param httpClient HTTP client
     */
    public NanopoolAccountRequestor(OkHttpClient httpClient) {
        super();
        this.httpClient = httpClient;
    }

    @Override
    public Account requestEthereumAccount(String walletAddress) throws AccountRequestorException {
        if (walletAddress == null || walletAddress.isEmpty()) {
            throw new AccountRequestorException(API_ERROR, "BAD_WALLET");
        }
        Account account = createAccountWithBalance(walletAddress);
        setReportedHashrate(account);
        return account;
    }

    /**
     * Creates account with balance.
     *
     * @param walletAddress wallet address
     * @return account with balance
     * @throws AccountRequestorException if there is any error in account requesting
     */
    private Account createAccountWithBalance(String walletAddress) throws AccountRequestorException {
        Request request = new Request.Builder().url(API_BALANCE_URL + walletAddress).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new AccountRequestorException(HTTP_ERROR, response.message());
            }
            try (ResponseBody body = response.body()) {
                JSONObject jsonResponse = new JSONObject(body.string());
                checkError(jsonResponse);
                return createAccount(walletAddress, jsonResponse);
            }
        } catch (JSONException e) {
            throw new AccountRequestorException(JSON_ERROR, e);
        } catch (IOException e) {
            throw new AccountRequestorException(HTTP_ERROR, e);
        }
    }

    /**
     * Sets reported hashrate to existing account.
     *
     * @param account created account
     * @throws AccountRequestorException if there is any error in account requesting
     */
    private void setReportedHashrate(Account account) throws AccountRequestorException {
        Request request = new Request.Builder().url(API_HASHRATE_URL + account.getWalletAddress()).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new AccountRequestorException(HTTP_ERROR, response.message());
            }
            try (ResponseBody body = response.body()) {
                JSONObject jsonResponse = new JSONObject(body.string());
                checkError(jsonResponse);
                account.setTotalHashrate(BigDecimal.valueOf(jsonResponse.getDouble("data")));
            }
        } catch (JSONException e) {
            throw new AccountRequestorException(JSON_ERROR, e);
        } catch (IOException e) {
            throw new AccountRequestorException(HTTP_ERROR, e);
        }
    }

    /**
     * Creates account from JSON response.
     *
     * @param walletAddress wallet address
     * @param jsonAccount account in JSON format
     * @return account
     */
    private Account createAccount(String walletAddress, JSONObject jsonAccount) {
        BigDecimal walletBalance = BigDecimal.valueOf(jsonAccount.getDouble("data"));
        return new Account(walletAddress, walletBalance, null);
    }

    /**
     * Check presence of error in JSON response.
     *
     * @param jsonResponse response in JSON format
     * @throws AccountRequestorException if there is any error in JSON response
     */
    private void checkError(JSONObject jsonResponse) throws AccountRequestorException {
        boolean status = jsonResponse.getBoolean("status");
        if (!status) {
            String errorMessage = jsonResponse.getString("error");
            throw new AccountRequestorException(API_ERROR, errorMessage);
        }
    }

}

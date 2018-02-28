package com.mined.in.pool.account.dwarfpool;

import java.math.BigDecimal;

import org.json.JSONException;
import org.json.JSONObject;

import com.mined.in.pool.account.Account;
import com.mined.in.pool.account.AccountException;

/**
 * Class for representing Dwarfpool account.
 *
 * @author Dmitry Tverdokhleb
 *
 */
public class DwarfpoolAccount extends Account {

    /**
     * Creates the Dwarfpool account instance.
     *
     * @param walletAddress wallet address
     * @param walletBalance wallet balance
     */
    private DwarfpoolAccount(String walletAddress, BigDecimal walletBalance) {
        super(walletAddress, walletBalance);
    }

    /**
     * Creates the Dwarfpool account instance from JSON format.
     *
     * @param jsonAccount account in JSON format
     * @return Dwarfpool account instance
     * @throws AccountException if there is any error in account creating
     */
    public static DwarfpoolAccount create(JSONObject jsonAccount) throws AccountException {
        try {
            String wallet = jsonAccount.getString("wallet");
            BigDecimal walletBalance = new BigDecimal(jsonAccount.getString("wallet_balance"));
            return new DwarfpoolAccount(wallet, walletBalance);
        } catch (JSONException e) {
            throw new AccountException(e);
        }
    }

}
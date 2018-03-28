package com.mined.in.worker;

import java.math.BigDecimal;

import com.mined.in.reward.Reward;

/**
 * Class for representing mined earnings.
 *
 * @author Dmitry Tverdokhleb
 *
 */
public class MinedEarnings {

    /** Balance of coins. */
    private final BigDecimal coinBalance;
    /** Balance in USD. */
    private final BigDecimal usdBalance;
    /** Coin price. */
    private final BigDecimal coinPrice;
    /** Estimated rewards. */
    private final Reward estimatedReward;

    /**
     * Creates the instance.
     *
     * @param coinBalance balance of coins
     * @param usdBalance balance in USD
     * @param coinPrice buy price
     * @param estimatedReward estimated reward
     */
    public MinedEarnings(BigDecimal coinBalance, BigDecimal usdBalance, BigDecimal coinPrice, Reward estimatedReward) {
        super();
        this.coinBalance = coinBalance;
        this.usdBalance = usdBalance;
        this.coinPrice = coinPrice;
        this.estimatedReward = estimatedReward;
    }

    /**
     * Gets the coins balance.
     *
     * @return the coins balance
     */
    public BigDecimal getCoinBalance() {
        return coinBalance;
    }

    /**
     * Gets the USD balance.
     *
     * @return the USD balance
     */
    public BigDecimal getUsdBalance() {
        return usdBalance;
    }

    /**
     * Gets the coin price.
     *
     * @return the coin price
     */
    public BigDecimal getCoinPrice() {
        return coinPrice;
    }

    /**
     * Gets the estimated rewards.
     *
     * @return the estimated rewards
     */
    public Reward getEstimatedReward() {
        return estimatedReward;
    }

}
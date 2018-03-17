package com.mined.in.bot.telegram;

import static com.pengrad.telegrambot.model.request.ParseMode.HTML;
import static java.math.RoundingMode.DOWN;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mined.in.bot.BotUpdates;
import com.mined.in.coin.Coin;
import com.mined.in.exchanger.Exchanger;
import com.mined.in.exchanger.pair.PairExecutor;
import com.mined.in.exchanger.pair.PairExecutorException;
import com.mined.in.exchanger.pair.PairExecutorFactory;
import com.mined.in.pool.Pool;
import com.mined.in.pool.account.AccountExecutor;
import com.mined.in.pool.account.AccountExecutorException;
import com.mined.in.pool.account.AccountExecutorFactory;
import com.mined.in.worker.MinedResult;
import com.mined.in.worker.MinedWorker;
import com.mined.in.worker.MinedWorkerFactory;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;

/**
 * Class for processing incoming updates from Telegram bot.
 *
 * @author Dmitry Tverdokhleb
 *
 */
public class TelegramUpdatesBot implements BotUpdates {

    /** Pengrad telegram bot. */
    private final com.pengrad.telegrambot.TelegramBot bot;
    /** Telegram message. */
    private TelegramMessage telegramMessage;
    /** Logger. */
    private final static Logger LOG = LoggerFactory.getLogger(TelegramUpdatesBot.class);
    /** Localization resources. */
    private final static ResourceBundle LOCALIZATION = ResourceBundle.getBundle(TelegramUpdatesBot.class.getSimpleName().toLowerCase());

    /**
     * Creates the instance for processing incoming updates from Telegram bot.
     *
     * @param token telegram token
     */
    public TelegramUpdatesBot(String token) {
        super();
        bot = new com.pengrad.telegrambot.TelegramBot(token);
    }

    @Override
    public void process(String request) {
        try {
            Update update = BotUtils.parseUpdate(request);
            Message message = update.message();
            if (message != null) {
                processMessage(message);
            }
            CallbackQuery callbackQuery = update.callbackQuery();
            if (callbackQuery != null) {
                processCallbackQuery(callbackQuery);
            }
        } catch (AccountExecutorException e) {
            telegramMessage.setErrorMessage(String.format(LOCALIZATION.getString("pool_error"), e.getMessage()));
            LOG.error("Incoming updates processing error", e);
        } catch (PairExecutorException e) {
            telegramMessage.setErrorMessage(String.format(LOCALIZATION.getString("exchanger_error"), e.getMessage()));
            LOG.error("Incoming updates processing error", e);
        } catch (Exception e) {
            telegramMessage.setErrorMessage(LOCALIZATION.getString("unexpected_error"));
            LOG.error("Incoming updates processing error", e);
        } finally {
            if (telegramMessage.onlySendMessage()) {
                sendMessage();
            } else {
                editMessage();
            }
        }
    }

    /**
     * Processes text message.
     *
     * @param message text message
     */
    private void processMessage(Message message) {
        telegramMessage = new TelegramMessage(message.chat().id(), message.messageId());
        if (message.text().equalsIgnoreCase("/start")) {
            telegramMessage.setMessageContent(LOCALIZATION.getString("start"));
        } else {
            createSupportingCoinsMessage();
        }
    }

    /**
     * Processes callback query.
     *
     * @param callbackQuery callback query
     * @throws AccountExecutorException if there is any error in account creating
     * @throws PairExecutorException if there is any error in pair creating
     */
    private void processCallbackQuery(CallbackQuery callbackQuery) throws AccountExecutorException, PairExecutorException {
        String data = callbackQuery.data();
        if (data == null || data.isEmpty()) {
            LOG.debug("CallbackQuery data is empty");
            return;
        }
        telegramMessage = new TelegramMessage(callbackQuery.message().chat().id(), callbackQuery.message().messageId());
        TelegramStepData stepData = new TelegramStepData(data);
        telegramMessage.setStepData(stepData);
        switch (stepData.getStep()) {
        case COIN: {
            createSupportingPoolsMessage(stepData);
            break;
        }
        case POOL: {
            createSupportingExchangersMessage(stepData);
            break;
        }
        case EXCHANGER: {
            telegramMessage.parsePreviousMessage(callbackQuery.message());
            String walletAddress = callbackQuery.message().replyToMessage().text();
            calculateAndCreateMinedResultMessage(stepData, walletAddress);
            break;
        }
        }
    }

    /**
     * Calculates and creates mined result message.
     *
     * @param stepData data of current step
     * @param walletAddress wallet address
     * @throws AccountExecutorException if there is any error in account creating
     * @throws PairExecutorException if there is any error in pair creating
     */
    private void calculateAndCreateMinedResultMessage(TelegramStepData stepData, String walletAddress)
            throws AccountExecutorException, PairExecutorException {
        Coin coin = stepData.getCoin();
        Pool pool = stepData.getPool();
        Exchanger exchanger = stepData.getExchanger();
        MinedResult minedResult = calculateMined(walletAddress, coin, pool, exchanger);
        createMinedResultMessage(coin, pool, exchanger, minedResult);
    }

    /**
     * Calculates mined and returns mined result.
     *
     * @param walletAddress wallet address
     * @param coin coin type
     * @param pool pool type
     * @param exchanger exchange type
     * @return mined result
     * @throws AccountExecutorException if there is any error in account creating
     * @throws PairExecutorException if there is any error in pair creating
     */
    private MinedResult calculateMined(String walletAddress, Coin coin, Pool pool, Exchanger exchanger)
            throws AccountExecutorException, PairExecutorException {
        AccountExecutor accountExecutor = AccountExecutorFactory.getAccountExecutor(pool);
        PairExecutor pairExecutor = PairExecutorFactory.getPairExecutor(exchanger);
        MinedWorker minedWorker = MinedWorkerFactory.getMinedWorker(coin, accountExecutor, pairExecutor);
        return minedWorker.calculate(walletAddress);
    }

    /**
     * Creates mined result message.
     *
     * @param coin coin type
     * @param pool pool type
     * @param exchanger exchange type
     * @param minedResult mined result
     */
    private void createMinedResultMessage(Coin coin, Pool pool, Exchanger exchanger, MinedResult minedResult) {
        BigDecimal coinBalance = minedResult.getCoinBalance().setScale(8, DOWN);
        BigDecimal usdBalance = minedResult.getUsdBalance().setScale(2, DOWN);
        BigDecimal buyPrice = minedResult.getBuyPrice().setScale(2, DOWN);
        BigDecimal sellPrice = minedResult.getSellPrice().setScale(2, DOWN);
        String minedResultMessage = LOCALIZATION.getString("mined_result");
        minedResultMessage = String.format(minedResultMessage,
                                           pool.getWebsite(),
                                           pool.getName().toUpperCase(),
                                           coinBalance,
                                           coin.getSymbol(),
                                           exchanger.getWebsite(),
                                           exchanger.getName().toUpperCase(),
                                           usdBalance,
                                           buyPrice,
                                           sellPrice);
        telegramMessage.setMessageContent(minedResultMessage);
    }

    /**
     * Creates supporting coins message.
     */
    private void createSupportingCoinsMessage() {
        List<Coin> coinList = Arrays.asList(Coin.values());
        InlineKeyboardButton[][] keyboardButtonArray = new InlineKeyboardButton[coinList.size()][1];
        for (int i = 0; i < coinList.size(); i++) {
            Coin coin = coinList.get(i);
            String coinSymbol = coin.getSymbol();
            keyboardButtonArray[i][0] = new InlineKeyboardButton(coinSymbol).callbackData(coinSymbol);
        }
        telegramMessage.setKeyboardMarkup(new InlineKeyboardMarkup(keyboardButtonArray));
        telegramMessage.setMessageContent(LOCALIZATION.getString("select_coin"));
    }

    /**
     * Creates supporting pools message.
     *
     * @param stepData data of current step
     */
    private void createSupportingPoolsMessage(TelegramStepData stepData) {
        List<Pool> poolList = Arrays.asList(Pool.values());
        poolList.sort((p1, p2) -> p1.getName().compareTo(p2.getName()));
        InlineKeyboardButton[][] keyboardButtonArray = new InlineKeyboardButton[poolList.size()][1];
        for (int i = 0; i < poolList.size(); i++) {
            Pool pool = poolList.get(i);
            String poolName = pool.getName();
            String callbackData = stepData.getCoin().getSymbol() + "_" + poolName;
            keyboardButtonArray[i][0] = new InlineKeyboardButton(poolName).callbackData(callbackData);
        }
        telegramMessage.setKeyboardMarkup(new InlineKeyboardMarkup(keyboardButtonArray));
        telegramMessage.setMessageContent(LOCALIZATION.getString("select_pool"));
    }

    /**
     * Creates supporting exchangers message.
     *
     * @param stepData data of current step
     */
    private void createSupportingExchangersMessage(TelegramStepData stepData) {
        List<Exchanger> exchangerList = Arrays.asList(Exchanger.values());
        exchangerList.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
        InlineKeyboardButton[][] keyboardButtonArray = new InlineKeyboardButton[exchangerList.size()][1];
        for (int i = 0; i < exchangerList.size(); i++) {
            Exchanger exchanger = exchangerList.get(i);
            String exchangerName = exchanger.getName();
            String callbackData = stepData.getCoin().getSymbol() + "_" + stepData.getPool().getName() + "_" + exchangerName;
            keyboardButtonArray[i][0] = new InlineKeyboardButton(exchangerName).callbackData(callbackData);
        }
        telegramMessage.setKeyboardMarkup(new InlineKeyboardMarkup(keyboardButtonArray));
        telegramMessage.setMessageContent(LOCALIZATION.getString("select_exchanger"));
    }

    /**
     * Sends a message.
     */
    private void sendMessage() {
        SendMessage request = new SendMessage(telegramMessage.getChatId(), telegramMessage.getFinalMessage());
        request.parseMode(HTML);
        if (telegramMessage.getKeyboardMarkup() != null) {
            request.replyMarkup(telegramMessage.getKeyboardMarkup());
        }
        if (telegramMessage.getMessageId() != null) {
            request.replyToMessageId(telegramMessage.getMessageId());
        }
        BaseResponse sendResponse = bot.execute(request);
        if (!sendResponse.isOk()) {
            LOG.error(sendResponse.description());
        }
    }

    /**
     * Edits a message.
     */
    private void editMessage() {
        String finalMessage = telegramMessage.getFinalMessage();
        EditMessageText request = new EditMessageText(telegramMessage.getChatId(), telegramMessage.getMessageId(), finalMessage);
        request.parseMode(HTML);
        if (telegramMessage.getKeyboardMarkup() != null) {
            request.replyMarkup(telegramMessage.getKeyboardMarkup());
        }
        BaseResponse response = bot.execute(request);
        if (!response.isOk()) {
            LOG.error(response.description());
        }
    }

}

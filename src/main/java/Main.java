import abstraction.ProtocolTasks;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.BlockChain;
import common.BlockData;
import configuration.Configuration;
import logging.Logger;
import network.NetworkManager;
import utils.Crypto;
import utils.Utils;
import utils.VDF;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by Mihael Berčič
 * on 26/03/2020 12:35
 * using IntelliJ IDEA
 */


public class Main {

    /**
     * Logger use:
     * Java -> Logger.INSTANCE.debug(...)
     * Kotlin -> Logger.debug(...)
     */

    public static Gson gson = new GsonBuilder()
            .setPrettyPrinting() // For debugging...
            .create();

    public static void main(String[] args) throws UnknownHostException {
        boolean isPathSpecified = args.length != 0;

        Logger.INSTANCE.debug("Starting...");
        Logger.INSTANCE.info("Path for config file specified: " + isPathSpecified);
        Logger.INSTANCE.info("Using " + (isPathSpecified ? "custom" : "default") + " configuration file...");

        String fileText = Utils.Companion.readFile(isPathSpecified ? args[0] : "./config.json");

        Configuration  configuration  = gson.fromJson(fileText, Configuration.class);
        Crypto         crypto         = new Crypto(".");
        VDF            vdf            = new VDF("http://localhost:" + configuration.getListeningPort() + "/vdf");
        BlockChain     blockChain     = new BlockChain(crypto, vdf, configuration);
        NetworkManager networkManager = new NetworkManager(configuration, crypto, blockChain);

        boolean isTrustedNode = InetAddress.getLocalHost().getHostAddress().equals(configuration.getTrustedNodeIP());

        String publicKey = crypto.getPublicKey();

        //the bootstrap node should start block production
        if (isTrustedNode) {
            Logger.INSTANCE.debug("Set synced and validator to true...");
            blockChain.setSynced(true);
            blockChain.setValidator(true);
            blockChain.addBlock(BlockData.Companion.genesisBlock(publicKey, 200000));
        }

        // TODO NOT Cool with possible NPEs yo

        //start producing blocks
        while (InetAddress.getLocalHost().getHostAddress().equals(configuration.getTrustedNodeIP())) { //only trusted node for now
            if (!blockChain.getChain().isEmpty()) {//oh god
                BlockData lastBlock = blockChain.getLastBlock();
                if (lastBlock != null) {
                    boolean areWeProducers = lastBlock.getConsensusNodes().contains(publicKey);
                    Logger.INSTANCE.debug("Are we block producers? " + areWeProducers);
                    if (areWeProducers) {
                        try {
                            vdf.runVDF(lastBlock.getDifficulty(), lastBlock.getHash(), lastBlock.getHeight() + 1);
                            break;
                        } catch (Exception e) {
                            Logger.INSTANCE.error(e.getMessage());
                        }
                    }
                }
            }
        }
        Logger.INSTANCE.consensus("Requesting inclusion into the validator set");
        networkManager.initiate(ProtocolTasks.requestInclusion, crypto.getPublicKey());
    }
}
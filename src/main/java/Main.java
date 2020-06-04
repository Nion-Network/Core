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

import java.io.IOException;
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
        Logger.INSTANCE.debug("Assembly without compile test...");
        boolean isPathSpecified = args.length != 0;

        Logger.INSTANCE.debug("Starting...");
        Logger.INSTANCE.info("Path for config file specified: " + isPathSpecified);
        Logger.INSTANCE.info("Using " + (isPathSpecified ? "custom" : "default") + " configuration file...");

        String fileText = Utils.Companion.readFile(isPathSpecified ? args[0] : "./config.json");

        Configuration configuration = gson.fromJson(fileText, Configuration.class);
        Crypto crypto = new Crypto(".");
        VDF vdf = new VDF("http://localhost:"+configuration.getListeningPort()+"/vdf");
        BlockChain blockChain = new BlockChain(crypto, vdf, configuration);
        NetworkManager networkManager = new NetworkManager(configuration, crypto, blockChain);
        blockChain.injectDependency(networkManager);
        //the bootstrap node should start block production
        if (InetAddress.getLocalHost().getHostAddress().equals(configuration.getTrustedNodeIP())) {
            blockChain.setSynced(true);
            blockChain.setValidator(true);
            blockChain.addBlock(common.BlockData.Block.genesisBlock(crypto.getPublicKey(), 200000));

        }

        //start producing blocks
        while (InetAddress.getLocalHost().getHostAddress().equals(configuration.getTrustedNodeIP())) { //only trusted node for now
            if (!blockChain.getChain().isEmpty()) {//oh god
                if (blockChain.getLastBlock().getConsensusNodes().contains(crypto.getPublicKey())) {//we are amongst the block producers
                    String proof = null;
                    try {
                        BlockData previous_block = blockChain.getLastBlock();
                        vdf.runVDF(previous_block.getDifficulty(), previous_block.getHash(),previous_block.getHeight()+1);
                        break;
                    } catch (IOException e) {
                        Logger.INSTANCE.error(e.getMessage());
                    } catch (InterruptedException e) {
                        Logger.INSTANCE.error(e.getMessage());
                    }
                }
            }
        }
        networkManager.initiate(ProtocolTasks.requestInclusion, crypto.getPublicKey());
        Logger.INSTANCE.consensus("Requesting inclusion into the validator set");
    }
}
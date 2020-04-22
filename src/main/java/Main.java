import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.Block;
import common.BlockChain;
import configuration.Configuration;
import logging.Logger;
import network.NetworkManager;
import utils.Crypto;
import utils.Utils;
import utils.VDF;

import java.io.IOException;

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

    public static void main(String[] args) {
        Logger.INSTANCE.debug("Assembly without compile test...");
        boolean isPathSpecified = args.length != 0;

        Logger.INSTANCE.debug("Starting...");
        Logger.INSTANCE.info("Path for config file specified: " + isPathSpecified);
        Logger.INSTANCE.info("Using " + (isPathSpecified ? "custom" : "default") + " configuration file...");

        String fileText = Utils.Companion.readFile(isPathSpecified ? args[0] : "./config.json");

        Configuration configuration = gson.fromJson(fileText, Configuration.class);
        Crypto crypto = new Crypto(".");
        //NetworkManager networkManager = new NetworkManager(configuration, crypto);

        Logger.INSTANCE.debug("Listening on port: " + configuration.getListeningPort());

        BlockChain blockChain = new BlockChain(new Block(crypto.getPublicKey()));
        Logger.INSTANCE.chain(gson.toJson(blockChain.getBlock(0)));
        //crypto test
        String message=" hello";
        String signature = null;
        try {
            signature = crypto.sign(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Logger.INSTANCE.info("Pub key: " + crypto.getPublicKey());
        Logger.INSTANCE.info("Signature: " + signature);
        try {
            Logger.INSTANCE.info("Is signature valid: " + crypto.verify(message,signature,crypto.getPublicKey()));
        } catch (Exception e) {
            e.printStackTrace();
        }


        VDF vdf = new VDF();
        String proof=null;
        try {
            proof =vdf.runVDF(1000, "aa");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Logger.INSTANCE.info(proof);
        Logger.INSTANCE.info("Is proof valid: " + vdf.verifyProof(1000,"aa",proof));
    }
}

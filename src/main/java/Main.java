import abstraction.ProtocolTasks;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import common.Block;
import common.BlockChain;
import configuration.Configuration;
import logging.Logger;
import network.NetworkManager;
import protocols.BlockPropagation;
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
        VDF vdf = new VDF();
        BlockChain blockChain;
        if(InetAddress.getLocalHost().getHostAddress().equals(configuration.getTrustedNodeIP())) {
            blockChain = new BlockChain(new Block(crypto.getPublicKey(), 200000), crypto, vdf, configuration);
        }else{
            blockChain = new BlockChain(null,crypto, vdf, configuration);
        }
        NetworkManager networkManager = new NetworkManager(configuration, crypto, blockChain);


        //start producing blocks
        while (true) {
            if(blockChain.getLastBlock()!=null) {//oh god
                if (blockChain.getLastBlock().getConsensus_nodes().contains(crypto.getPublicKey())) {//we are amongst the block producers
                    String proof = null;
                    try {
                        Block previous_block = blockChain.getLastBlock();
                        proof = vdf.runVDF(previous_block.getDifficulty(), previous_block.getHash());
                        Block new_block = new Block(previous_block, proof, crypto);
                        networkManager.initiate(ProtocolTasks.newBlock, new_block);
                        String outcome = blockChain.addBlock(new_block, networkManager);
                        Logger.INSTANCE.info("New Block forged " + outcome);
                    } catch (IOException e) {
                        Logger.INSTANCE.error(e.getMessage());
                    } catch (InterruptedException e) {
                        Logger.INSTANCE.error(e.getMessage());
                    }
                }
            }
        }

        /*
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

         */
    }
}
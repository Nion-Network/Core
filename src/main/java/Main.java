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
        BlockChain blockChain = new BlockChain(crypto,vdf,configuration);
        NetworkManager networkManager = new NetworkManager(configuration, crypto, blockChain);
        blockChain.injectDependency(networkManager);
        //the bootstrap node should start block production
        if(InetAddress.getLocalHost().getHostAddress().equals(configuration.getTrustedNodeIP())) {
            blockChain.addBlock(new Block(crypto.getPublicKey(), 200000));
        }

        //start producing blocks
        while (InetAddress.getLocalHost().getHostAddress().equals(configuration.getTrustedNodeIP())) { //only trusted node for now
            if(blockChain.getLastBlock()!=null) {//oh god
                if (blockChain.getLastBlock().getConsensus_nodes().contains(crypto.getPublicKey())) {//we are amongst the block producers
                    String proof = null;
                    try {
                        Block previous_block = blockChain.getLastBlock();
                        proof = vdf.runVDF(previous_block.getDifficulty(), previous_block.getHash());
                        Block new_block = new Block(previous_block, proof, crypto);
                        String outcome = blockChain.addBlock(new_block);
                        Logger.INSTANCE.info("New Block forged " + outcome);
                        networkManager.initiate(ProtocolTasks.newBlock, new_block);
                    } catch (IOException e) {
                        Logger.INSTANCE.error(e.getMessage());
                    } catch (InterruptedException e) {
                        Logger.INSTANCE.error(e.getMessage());
                    }
                }
            }
        }
    }
}

/*
\"000ee82128687d51587c5217d1427940c77af7769b9aa0541095785caee611f4b22e8f18a9aa37d2c76b6e700c8112a932d651e88c226a84e40ee29f364a208f8d94c510d192a1e45eaa0c3ea1ac9044d6a13d8445f4363ba11c648541544eee4d9c7b7f19628542fafe035ce605321f97a8f198d90b7137169bed944ea871ac990008423e263b5c6b750928b39d547b16f7fe2e20d3b49cfa7cb14c9546156667f9b42aad43c135db7a788212e7f078014560038fb214fd85083820e8b223eccad7ec461cb1f2bd912faf929304ab1bc84c79bd296841488c91deab84c4b3e5f7da4ecc01cc506ae889025383377917d6838e821cc6b606bdf634598749956f37030000b611381659401e51d99072d82960b852642e2ae69fb7d98e67c38dbc20f9590d9da68a517d741f14e477e1ba8d2baec33ef9ca88d7eccfc53ebd7730b8e3e851b59968cdc11228457df4e52d2407ea9acb542dc2eb1faaf8d62273b30ff729416ff37bbaaf4e1e42ed9dd4e2e4c52b3d37520cdc77047ae821e03d76898c520000a9c7d83c369b181f5140e7060551d9d6d23beefebbb9da77c128823d1c75e6543d9bef1b96ca636468a30f3f8cc80a084d5f142f3b919420740472df0dedcd801d02103756b923d11622d23bbb75bdc80d9e61725d7ca92f5ce1e7e4f07a91b5fd8693140839d5a93f38e2bc47f930b37e81f75da8f001948de7b6d6db0c21\\n\",\n      \"block_producer\": \"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAgvKVmyp2GBa3aAcc/0P3+EOQ/1Osv2Um\\r\\nGdM/Hv/XhT/ElQGoxeqx1dyw/okgfZrycLU9EuS3Q4s2nGW8PbZ2kpIAEY0DiW3NBp4hLvPK/Dn9\\r\\nl2I4YCk9TZdvz3sv8vOXm5yvGpd2bcHB99JFoKqaL8DfKKT/nieeCfm5V+nFpqDqASrjVGPv2//b\\r\\n/3hA0hp8xTV+iKX0RhuXnfKaun6yHrtzgOnBnkgLVAhaJeK33DEoWVR0cKdktg8TleYVeLRDEtVX\\r\\nShwXYPSvoiwGA5gnUqSKnlNlqA7iatpp9Zds0hx5SV7eKebnYCzfE8KHJ7lEwlbcW2bLyfjrTVHO\\r\\nAhpvpwIDAQAB\",\n      \"timestamp\": 1588342481026,\n      \"consensus_nodes\": [\n        \"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAgvKVmyp2GBa3aAcc/0P3+EOQ/1Osv2Um\\r\\nGdM/Hv/XhT/ElQGoxeqx1dyw/okgfZrycLU9EuS3Q4s2nGW8PbZ2kpIAEY0DiW3NBp4hLvPK/Dn9\\r\\nl2I4YCk9TZdvz3sv8vOXm5yvGpd2bcHB99JFoKqaL8DfKKT/nieeCfm5V+nFpqDqASrjVGPv2//b\\r\\n/3hA0hp8xTV+iKX0RhuXnfKaun6yHrtzgOnBnkgLVAhaJeK33DEoWVR0cKdktg8TleYVeLRDEtVX\\r\\nShwXYPSvoiwGA5gnUqSKnlNlqA7iatpp9Zds0hx5SV7eKebnYCzfE8KHJ7lEwlbcW2bLyfjrTVHO\\r\\nAhpvpwIDAQAB\"\n      ]\n    },\n    {\n      \"hash\": \"0de5e1f197401d12f51a009e90f63e5be5e712434ff1799064472a0836c5f04c\",\n      \"previous_hash\": \"e64d04ed70ed91be3b643f4276e3b4f8baaff0ee3f17a0867df9f219bd6a63f5\",\n      \"height\": 3,\n      \"ticket\": 0,\n      \"difficulty\": 200000,\n      \"vdf_proof\": \"0084fd710eb69ba1acab1357e7d91f93ec3feeffe09fc89e3e05dc9bcc03802fa006eae9417cc1e415b0404498ed4fef839eab7d7a4b0c9929542626a75b9e4ae354190afb9f6e1a4a47f2cdef714beff05f34531e1d60ca0f5a5c59cbc794c4d08611300b7f8f140b8150360b31180d9406d5307412f1df5508f3888c4c6d3986007e8fbf73805861022058128cff0073f02c8fbe90183d855476422509468e1ca99f2d004d00f3aa3dd039a58c5e486986fbd0f3381dc471e84180d8a45861493b5ed03136fd364efb373068849b8f177e07949b79bd2dc9491bf8e8b1067b1baa98905087f9d5a1b236648d8ccb1f31c8b5149e25a92803a5a5a5078bea251fe90082e8b6d0da5f7adc6a42db476f8ef283054fe7730d20af649eb35647bb5c130b86e7968b5f51d8f68baabed8a704716042e3508f1270d541776bef0bcb6838b838e30c1ad1678d3c74def6efd77dd8c563e15109bfb7d0339b5e07915714efbed4a364a0e95b804b427d61302bb74ef2ce1658a6700804d64a97ad688efec0a8ff902121223ac254d89afd0ffd8cc021485d107fbc9255a4bc22f974aec2e3d1b75643a98ad53378bbfaca266dba15b00f63c0582ff1324ca7125e8fe1422838a6e42a82009a098c0a0a818ba1e8393052c50c3d25da7fb4630a83adc99c202d217f875f4919475ff006354be55e4003998829ee4d8c5538fe7632b17a5c972619\\n\",\n      \"block_producer\": \"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAgvKVmyp2GBa3aAcc/0P3+EOQ/1Osv2Um\\r\\nGdM/Hv/XhT/ElQGoxeqx1dyw/okgfZrycLU9EuS3Q4s2nGW8PbZ2kpIAEY0DiW3NBp4hLvPK/Dn9\\r\\nl2I4YCk9TZdvz3sv8vOXm5yvGpd2bcHB99JFoKqaL8DfKKT/nieeCfm5V+nFpqDqASrjVGPv2//b\\r\\n/3hA0hp8xTV+iKX0RhuXnfKaun6yHrtzgOnBnkgLVAhaJeK33DEoWVR0cKdktg8TleYVeLRDEtVX\\r\\nShwXYPSvoiwGA5gnUqSKnlNlqA7iatpp9Zds0hx5SV7eKebnYCzfE8KHJ7lEwlbcW2bLyfjrTVHO\\r\\nAhpvpwIDAQAB\",\n      \"timestamp\": 1588342498641,\n      \"consensus_nodes\":
 */
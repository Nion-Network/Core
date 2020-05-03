package common;
import abstraction.ProtocolTasks;
import configuration.Configuration;
import logging.Logger;
import network.NetworkManager;
import org.apache.commons.codec.digest.DigestUtils;
import utils.Crypto;
import utils.VDF;

import java.util.*;

public class BlockChain {
    private List<Block> chain;
    private Crypto crypto;
    private VDF vdf;
    private ArrayList<String> lottery_results;
    private int expected_block_producer;
    private Timer timer;
    private TimerTask nextEpoch;
    private Configuration configuration;
    private NetworkManager networkManager;
    private ArrayList<String> pending_inclusion_requests;
    private boolean validator_node = false;

    public BlockChain(Crypto crypto, VDF vdf, Configuration configuration){
        this.pending_inclusion_requests = new ArrayList<>();
        this.chain = new ArrayList<Block>();
        this.crypto = crypto;
        this.vdf = vdf;
        this.configuration =configuration;
        nextEpoch = new TimerTask() {
            @Override
            public void run() {
                expected_block_producer++;
                Logger.INSTANCE.chain("Epoch expired : " + expected_block_producer);
            }
        };
    }
    public String addBlock(Block block){
        if(chain.size()>0) {
            if (getLastBlock().getHash().equals(block.getPrevious_hash())) { //correct chain
                if (vdf.verifyProof(getLastBlock().getDifficulty(), getLastBlock().getHash(), block.getVdf_proof())) { //proof valid
                    this.lottery_results = sortByTicket(block.getVdf_proof(), block.getConsensus_nodes());
                    if (block.getBlock_producer().equals(lottery_results.get(expected_block_producer))) { //lottery winner
                        this.chain.add(block);
                        Logger.INSTANCE.chain("Block " + block.getHeight() + " was added to the chain");
                        if(!validator_node && block.getConsensus_nodes().contains(crypto.getPublicKey())){
                            validator_node=!validator_node;
                            Logger.INSTANCE.consensus("We were included in the validator set at block: " +block.getHeight());
                        }else if (!block.getConsensus_nodes().contains(crypto.getPublicKey()) && validator_node){
                            validator_node=!validator_node;
                            Logger.INSTANCE.consensus("We were removed from the validator set at block: " +block.getHeight());
                        }
                        //schedule epoch sliding window
                        this.expected_block_producer = 0;
                        if (timer != null) {
                            nextEpoch.cancel();
                            timer.cancel();
                            timer.scheduleAtFixedRate(nextEpoch,0 , configuration.getEpochDuration());
                        }
                        return block.getHash();
                    } else {
                        Logger.INSTANCE.chain("Block " + block.getHash() + " not produced by lottery winner!");
                    }
                } else {
                    Logger.INSTANCE.chain("VDF proof for block " + block.getHash() + " is invalid!");
                }
            } else {
                if(Math.abs(getLastBlock().getHeight() - block.getHeight())>1){ //we're out of sync
                    Logger.INSTANCE.chain("Node fell out of sync 2.");
                    networkManager.initiate(ProtocolTasks.requestBlocks,getLastBlock().getHeight());
                }else {
                    Logger.INSTANCE.chain("Hash miss-match on candidate block: " + block.getHash());
                }
            }
        }else if(block.getHeight()==0){
            Logger.INSTANCE.chain("Adding genesis block ");
            this.chain.add(block);//we add genesis block without challenge
        }else{
            if(chain.size()==0) {
                networkManager.initiate(ProtocolTasks.requestBlocks, 0);
            }else {
                networkManager.initiate(ProtocolTasks.requestBlocks,getLastBlock().getHeight());
            }
        }
        return null;
    }
    public boolean isValid(){
        return true;
    }
    public Block getBlock(int height){
        return this.chain.get(height);
    }
    public Block getLastBlock(){
        if(chain.size()>0) {
            return this.chain.get(this.chain.size() - 1);
        }else{
            return null;
        }
    }

    public ArrayList<String> sortByTicket(String vdf_proof, ArrayList<String> candidates){
        candidates.sort(Comparator.comparingInt((String S) -> distance(vdf_proof, S)));
        if(candidates.size()>0) {
            Logger.INSTANCE.chain("First ticket number: " + distance(vdf_proof, candidates.get(0)));
            Logger.INSTANCE.chain("Last ticket number: " + distance(vdf_proof, candidates.get(candidates.size()-1)));
        }
        return candidates;
    }
    public int distance(String proof, String node_id){
        node_id = DigestUtils.sha256Hex(node_id);
        Long seed = Long.parseUnsignedLong(proof.substring(0,16),16);
        Random random = new Random(seed);
        double draw = random.nextDouble();
        double front = Math.abs(Long.parseUnsignedLong(node_id.substring(0,16),16) / (double)Long.MAX_VALUE);
        int ticket = (int) (Math.abs(front - draw) * 1000000);
        return ticket;
    }

    public List<Block> getChain() {
        return chain;
    }
    public void syncChain(List<Block> blocks){
        for (Block b : blocks) {
            Logger.INSTANCE.chain("Chain size: "+ chain.size() + " Adding block " +b.getHeight() + " : " +b.getHash());
            addBlock(b);
        }
    }
    public void injectDependency(NetworkManager networkManager){
        this.networkManager = networkManager;
    }
    public void addInclusionRequest(String publicKey){
        this.pending_inclusion_requests.add(publicKey);
    }
    public ArrayList<String> getPending_inclusion_requests(){
        return pending_inclusion_requests;
    }
}

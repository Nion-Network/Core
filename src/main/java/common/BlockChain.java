package common;
import abstraction.ProtocolTasks;
import configuration.Configuration;
import logging.Logger;
import network.NetworkManager;
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

    public BlockChain(Crypto crypto, VDF vdf, Configuration configuration){
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
                        //schedule epoch sliding window
                        this.expected_block_producer = 0;
                        if (timer != null) {
                            nextEpoch.cancel();
                            timer.cancel();
                            timer.schedule(nextEpoch, configuration.getEpochDuration());
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
        //candidates.sort(((o1, o2) -> distance(vdf_proof,o1) - distance(vdf_proof,o2)));
        candidates.sort(Comparator.comparingInt((String S) -> distance(vdf_proof, S)));
        if(candidates.size()>0) {
            Logger.INSTANCE.chain("Ticket number: " + distance(vdf_proof, candidates.get(0)));
        }
        return candidates;
    }
    public int distance(String proof, String node_id){
        node_id = crypto.computeHash(node_id);
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
        Logger.INSTANCE.chain("Start sync at height: " + chain.size());
        for (Block b :
                blocks) {
            Logger.INSTANCE.chain("Chain size: "+ chain.size() + " Adding block " +b.getHeight() + " : " +b.getHash());
            addBlock(b);
        }
        //blocks.stream().forEach(Block -> chain.add(Block));
        Logger.INSTANCE.chain("Ended sync at height: " +chain.size());
    }
    public void injectDependency(NetworkManager networkManager){
        this.networkManager = networkManager;
    }
}

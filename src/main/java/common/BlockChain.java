package common;
import configuration.Configuration;
import logging.Logger;
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

    public BlockChain(Block genesis, Crypto crypto, VDF vdf,  Configuration configuration){
        this.chain = new ArrayList<Block>();
        if(genesis!=null) this.chain.add(genesis);
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
                    Logger.INSTANCE.chain("Node fell out of sync.");
                }else {
                    Logger.INSTANCE.chain("Hash miss-match on candidate block: " + block.getHash());
                }
            }
        }else{
            this.chain.add(block);//this should be removed in production. We accept any genesis block
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
}

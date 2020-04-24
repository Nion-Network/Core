package common;
import logging.Logger;
import utils.Crypto;

import java.util.*;

public class BlockChain {
    private List<Block> chain;
    private Crypto crypto;

    public BlockChain(Block genesis, Crypto crypto){
        this.chain = new ArrayList<Block>();
        this.chain.add(genesis);
        this.crypto = crypto;
    }
    public String addBlock(Block block){
        if(chain.get(chain.size()-1).getHash().equals(block.getPrevious_hash())) {
            this.chain.add(block);
            return block.getHash();
        }else{
            return null;
        }
    }
    public boolean isValid(){
        return true;
    }
    public Block getBlock(int height){
        return this.chain.get(height);
    }
    public Block getLastBlock(){
        return this.chain.get(this.chain.size()-1);
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
}

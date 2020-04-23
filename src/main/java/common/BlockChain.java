package common;
import utils.Crypto;

import java.util.ArrayList;
import java.util.List;

public class BlockChain {
    private List<Block> chain;
    private Crypto crypto;

    public BlockChain(Block genesis){
        this.chain = new ArrayList<Block>();
        this.chain.add(genesis);
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
}

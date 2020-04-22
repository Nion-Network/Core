package common;
import java.util.ArrayList;
import java.util.List;

public class BlockChain {
    private List<Block> chain;

    public BlockChain(Block genesis){
        this.chain = new ArrayList<Block>();
        this.chain.add(genesis);
    }
    public boolean addBlock(Block block){
        if(chain.get(chain.size()-1).getHash().equals(block.getHash())) {
            this.chain.add(block);
            return true;
        }else{
            return false; //hash miss-match
        }
    }
    public boolean isValid(){
        return true;
    }
    public Block getBlock(int height){
        return this.chain.get(height);
    }
}

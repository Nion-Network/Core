package common;
import utils.Crypto;
import utils.VDF;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class Block {
    private String hash;
    private String previous_hash;
    private int height;
    private int ticket;
    private int difficulty;
    private String vdf_proof;
    private String block_producer;
    private Long timestamp;
    private Boolean block_final;
    private ArrayList<String> consensus_nodes;
    //TODO: Add a hashmap (node_id , app_id) as block body


    public Block(String previous_hash, int height, int ticket, int difficulty, String vdf_proof, String block_producer, Boolean block_final, ArrayList<String> consensus_nodes) {
        this.previous_hash = previous_hash;
        this.height = height;
        this.ticket = ticket;
        this.difficulty = difficulty;
        this.vdf_proof = vdf_proof;
        this.block_producer = block_producer;
        this.timestamp = System.currentTimeMillis();
        this.block_final = block_final;
        this.consensus_nodes = consensus_nodes;
        this.hash = computeHash();
    }
    //constructor for genesis  block
    public Block(String trusted_node_pub_key){
        this.consensus_nodes= new ArrayList<String>();
        this.consensus_nodes.add(trusted_node_pub_key);
        this.difficulty = 100000;
        this.hash = computeHash();
    }
    public Block (Block previous_block, String vdf_proof, Crypto crypto){
        this.vdf_proof = vdf_proof;
        this.height = previous_block.height+1;
        this.difficulty = previous_block.difficulty; //TODO: dificulty adjustment algorithm
        this.block_producer = crypto.getPublicKey();
        this.timestamp = System.currentTimeMillis();
        this.previous_hash = previous_block.getHash();
        this.consensus_nodes = new ArrayList<String>();
        this.consensus_nodes.add(crypto.getPublicKey());
        //TODO: ticket allocation
        this.hash = computeHash();
    }

    public String computeHash(){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = previous_hash+height+ticket+difficulty+vdf_proof+block_producer+timestamp+consensus_nodes;
            byte[] data = md.digest(input.getBytes(StandardCharsets.UTF_8));
            BigInteger number = new BigInteger(1, data);
            StringBuilder hex = new StringBuilder(number.toString(16));
            while(hex.length()<32){hex.insert(0,'0');}
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public ArrayList<String> getConsensus_nodes() {
        return consensus_nodes;
    }

    public void setConsensus_nodes(ArrayList<String> consensus_nodes) {
        this.consensus_nodes = consensus_nodes;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getPrevious_hash() {
        return previous_hash;
    }

    public void setPrevious_hash(String previous_hash) {
        this.previous_hash = previous_hash;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getTicket() {
        return ticket;
    }

    public void setTicket(int ticket) {
        this.ticket = ticket;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public String getVdf_proof() {
        return vdf_proof;
    }

    public void setVdf_proof(String vdf_proof) {
        this.vdf_proof = vdf_proof;
    }

    public String getBlock_producer() {
        return block_producer;
    }

    public void setBlock_producer(String block_producer) {
        this.block_producer = block_producer;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getBlock_final() {
        return block_final;
    }

    public void setBlock_final(Boolean block_final) {
        this.block_final = block_final;
    }
}

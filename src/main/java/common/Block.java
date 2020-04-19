package common;

public class Block {
    String hash;
    String previous_hash;
    int height;
    int ticket;
    int difficulty;
    String vdf_proof;
    String block_producer;
    Long timestamp;
    Boolean block_final;
    //TODO: Add a hashmap (node_id , app_id) as block body
}

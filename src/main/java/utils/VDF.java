package utils;

import logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class VDF {
    //this class assumes vdf-cli is available in the path (https://docs.rs/vdf/0.1.0/vdf/)
    private Process vdfProcess;
    private Process proofProcess;
    private Runtime runtime;
    private String  url;

    public VDF(String url) {
        this.runtime = Runtime.getRuntime();
        this.url     = url;
    }

    public void runVDF(int difficulty, String hash, int height) throws IOException, InterruptedException {
        Logger.INSTANCE.debug("VDF HASH: " +hash +" for height: "+ height);
        if (vdfProcess != null && vdfProcess.isAlive()) {
            kill();
            Logger.INSTANCE.info("A VDF process is already running. It was killed");
        }
        this.vdfProcess = runtime.exec("vdf-cli " + hash + " " + difficulty + " -u " + this.url + " -b " + height);

        /*
        int exitVal = vdf_process.waitFor();
        if (exitVal == 0) {
            return builder.toString();
        } else {
            //TODO throw exception to be handled by the implementing class
            Logger.INSTANCE.error("Error producing VDF " + exitVal);
            return null;
        }*/
    }

    public boolean verifyProof(int difficulty, String hash, String proof) {
        try {
            Logger.INSTANCE.debug("Verifying proof: Hash:" + hash + " proof: " + DigestUtils.sha256Hex(proof));
            proofProcess = runtime.exec("vdf-cli " + hash + " " + difficulty + " " + proof);
            StringBuffer   out    = new StringBuffer();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proofProcess.getInputStream()));
            String         line;
            while ((line = reader.readLine()) != null) out.append(line).append("\n");

            int exit = 0;
            try {
                exit = proofProcess.waitFor();
            } catch (InterruptedException e) {
                return false;
            }
            if(exit != 0) Logger.INSTANCE.error("Error with verifying proof...");
            return exit == 0 && out.toString().trim().equals("Proof is valid");
        } catch (IOException e) {
            Logger.INSTANCE.error("IO Exception verifying VDF " + e.toString());
            //should never happen, reject block if it does and re-sync the chain
            return false;
        }
    }

    public void kill() {
        Process killer = null;
        try {
            killer = Runtime.getRuntime().exec("ps -ef | grep vdf-cli | grep -v \"grep\" | awk '{print $2}' | xargs kill; ");
            killer.waitFor();
        } catch (IOException | InterruptedException e) {
            Logger.INSTANCE.error("Failed to kill VDF " + e.getMessage());
        }
    }
}

package utils;

import logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class VDF {
    //this class assumes vdf-cli is available in the path (https://docs.rs/vdf/0.1.0/vdf/)
    private Process vdf_process;
    private Process proof_process;
    private Runtime rt;
    private String  url;

    public VDF(String url) {
        this.rt  = Runtime.getRuntime();
        this.url = url;
    }

    public void runVDF(int difficulty, String hash, int height) throws IOException, InterruptedException {
        if (vdf_process != null && vdf_process.isAlive()) {
            kill();
            Logger.INSTANCE.info("A VDF process is already running. It was killed");
        }
        this.vdf_process = rt.exec("vdf-cli " + hash + " " + difficulty + " -u " + this.url + " -b " + height);
        StringBuilder  output = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(vdf_process.getInputStream()));
        String         line;
        while ((line = reader.readLine()) != null) {
            output.append(line + "\n");
        }/*
        int exitVal = vdf_process.waitFor();
        if (exitVal == 0) {
            return output.toString();
        } else {
            //TODO throw exception to be handled by the implementing class
            Logger.INSTANCE.error("Error producing VDF " + exitVal);
            return null;
        }*/
    }

    public boolean verifyProof(int difficulty, String hash, String proof) {
        try {
            proof_process = rt.exec("vdf-cli " + hash + " " + difficulty + " " + proof);
            StringBuffer   out    = new StringBuffer();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proof_process.getInputStream()));
            String         line;
            while ((line = reader.readLine()) != null) {
                out.append(line + "\n");
            }
            int exit = 0;
            try {
                exit = proof_process.waitFor();
            } catch (InterruptedException e) {
                return false;
            }
            if (exit == 0) { //success, parse out
                if (out.toString().trim().equals("Proof is valid")) {
                    return true;
                } else {
                    return false;
                }
            } else {
                //Logger.INSTANCE.error("Error verifying VDF " + out.toString().trim());
                return false;
            }
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

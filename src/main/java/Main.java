import logging.Logger;
import manager.ApplicationManager;
import utils.KotlinVDF;
import utils.Utils;
import utils.VDF;

import java.net.UnknownHostException;

/**
 * Created by Mihael Berčič
 * on 26/03/2020 12:35
 * using IntelliJ IDEA
 */


public class Main {

    public static void main(String[] args) throws UnknownHostException {

        KotlinVDF vdf = new KotlinVDF();
        Logger.INSTANCE.error(vdf.findProof(100, "aa", 0));

        System.exit(-1);


        boolean isPathSpecified = args.length != 0;

        Logger.INSTANCE.startInputListening();
        Logger.INSTANCE.debug("Starting...");
        Logger.INSTANCE.info("Path for config file specified: " + isPathSpecified);
        Logger.INSTANCE.info("Using " + (isPathSpecified ? "custom" : "default") + " configuration file...");

        String fileText = Utils.Companion.readFile(isPathSpecified ? args[0] : "./config.json");

        ApplicationManager manager = new ApplicationManager(fileText);

    }

}
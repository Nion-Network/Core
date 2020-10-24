import logging.Logger;
import manager.NetworkManager;
import utils.Utils;

/**
 * Created by Mihael Berčič
 * on 26/03/2020 12:35
 * using IntelliJ IDEA
 */


public class Main {

    public static void main(String[] args) {
        boolean isPathSpecified = args.length != 0;

        Logger.INSTANCE.startInputListening();
        Logger.INSTANCE.debug("Starting...");
        Logger.INSTANCE.info("Path for config file specified: " + isPathSpecified);
        Logger.INSTANCE.info("Using " + (isPathSpecified ? "custom" : "default") + " configuration file...");

        String fileText = Utils.Companion.readFile(isPathSpecified ? args[0] : "./config.json");

        NetworkManager network = new NetworkManager(fileText);
        network.start();
    }

}
import logging.Logger;
import manager.NetworkManager;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Mihael Berčič
 * on 26/03/2020 12:35
 * using IntelliJ IDEA
 */


public class Main {

    public static void main(String[] args) {
        List<String> arguments = Arrays.asList(args);

        int pathArgumentIndex    = arguments.indexOf("-c");
        int portArgumentIndex    = arguments.indexOf("-p");
        int loggingArgumentIndex = arguments.indexOf("-l");

        boolean isPathSpecified  = pathArgumentIndex >= 0;
        boolean isPortSpecified  = portArgumentIndex >= 0;
        boolean isLoggingEnabled = loggingArgumentIndex >= 0;

        String configurationPath = isPathSpecified ? args[pathArgumentIndex + 1] : "./config.json";
        int    listeningPort     = isPortSpecified ? Integer.parseInt(args[portArgumentIndex + 1]) : 5000;

        Logger.INSTANCE.debug("Starting...");
        Logger.INSTANCE.info("Path for config file specified: " + isPathSpecified);
        Logger.INSTANCE.info("Using " + listeningPort + " port.");
        Logger.INSTANCE.info("Using " + configurationPath + " configuration file...");

        NetworkManager network = new NetworkManager(configurationPath, listeningPort);
        network.start();
    }

}
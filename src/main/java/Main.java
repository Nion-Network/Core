import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import configuration.Configuration;
import logging.Logger;
import network.NetworkManager;
import utils.Utils;

/**
 * Created by Mihael Berčič
 * on 26/03/2020 12:35
 * using IntelliJ IDEA
 */


public class Main {

    /**
     * Logger use:
     * Java -> Logger.INSTANCE.debug(...)
     * Kotlin -> Logger.debug(...)
     */

    public static Gson gson = new GsonBuilder()
            .setPrettyPrinting() // For debugging...
            .create();

    public static void main(String[] args) {
        boolean isPathSpecified = args.length != 0;

        Logger.INSTANCE.debug("Starting...");
        Logger.INSTANCE.info("Path for config file specified: " + isPathSpecified);
        Logger.INSTANCE.info("Using " + (isPathSpecified ? "custom" : "default") + " configuration file...");

        String fileText = Utils.Companion.readFile(isPathSpecified ? args[0] : "./config.json");

        Configuration configuration = gson.fromJson(fileText, Configuration.class);
        NetworkManager networkManager = new NetworkManager(configuration.getListeningPort(), configuration.getMaxNodes());

        Logger.INSTANCE.debug("Listening on port: " + configuration.getListeningPort());
    }
}

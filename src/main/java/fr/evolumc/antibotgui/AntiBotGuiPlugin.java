package fr.evolumc.antibotgui;

import org.bukkit.GameMode;
import org.bukkit.plugin.java.JavaPlugin;

public class AntiBotGuiPlugin extends JavaPlugin {

    private ValidatedIpStore ipStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ipStore = new ValidatedIpStore(this);
        getServer().getPluginManager().registerEvents(new VerificationListener(this), this);

        getLogger().info("AntiBotGUI activé. Serveur configuré : " + getServerName());
    }

    public ValidatedIpStore getIpStore() {
        return ipStore;
    }

    public String getServerName() {
        return getConfig().getString("server-name", "EvoluMC");
    }

    public int getMaxAttempts() {
        return getConfig().getInt("max-attempts", 5);
    }

    public GameMode getDefaultGameMode() {
        try {
            return GameMode.valueOf(getConfig().getString("default-gamemode", "SURVIVAL").toUpperCase());
        } catch (IllegalArgumentException e) {
            return GameMode.SURVIVAL;
        }
    }
}

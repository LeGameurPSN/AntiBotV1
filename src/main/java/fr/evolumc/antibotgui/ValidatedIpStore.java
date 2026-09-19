package fr.evolumc.antibotgui;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stocke les IP validées une fois pour toutes dans un fichier texte
 * (validated_ips.txt) à la racine du dossier du plugin.
 */
public class ValidatedIpStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Set<String> validatedIps = new LinkedHashSet<>();

    public ValidatedIpStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "validated_ips.txt");
        load();
    }

    private void load() {
        validatedIps.clear();
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    validatedIps.add(line);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lecture validated_ips.txt : " + e.getMessage());
        }
    }

    public boolean isValidated(String ip) {
        return validatedIps.contains(ip);
    }

    public void validate(String ip) {
        if (validatedIps.add(ip)) {
            appendLine(ip);
        }
    }

    private void appendLine(String ip) {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                writer.println(ip);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur écriture validated_ips.txt : " + e.getMessage());
        }
    }
}

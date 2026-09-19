package fr.evolumc.antibotgui;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vérification style "AuthMe" : tout se passe au chat, avant même que
 * le joueur ait accès au lobby. Aucune enclume n'est utilisée.
 *
 * Le joueur tape sa réponse directement dans le chat (comme /login sur
 * AuthMe) ; le message est intercepté, annulé (jamais diffusé
 * publiquement) et traité comme réponse à l'étape en cours.
 */
public class VerificationListener implements Listener {

    private final AntiBotGuiPlugin plugin;
    private final ValidatedIpStore ipStore;

    private final Map<UUID, VerificationSession> sessions =
            new ConcurrentHashMap<>();

    public VerificationListener(AntiBotGuiPlugin plugin) {
        this.plugin = plugin;
        this.ipStore = plugin.getIpStore();
    }

    private String getIp(Player player) {
        if (player.getAddress() == null) {
            return "unknown";
        }

        if (player.getAddress().getAddress() == null) {
            return "unknown";
        }

        return player.getAddress()
                .getAddress()
                .getHostAddress();
    }

    private boolean isVerifying(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /*
     * =========================
     * CONNEXION
     * =========================
     */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();
        String ip = getIp(player);

        /*
         * IP déjà validée :
         * aucune vérification.
         */
        if (ipStore.isValidated(ip)) {
            return;
        }

        startVerification(player);
    }

    private void startVerification(Player player) {

        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        player.setFlying(false);
        player.setAllowFlight(false);

        String code = generateCaptchaCode();

        sessions.put(
                player.getUniqueId(),
                new VerificationSession(code)
        );

        /*
         * On attend un tick afin de laisser le joueur
         * être complètement chargé avant d'afficher le prompt.
         */
        new BukkitRunnable() {

            @Override
            public void run() {

                if (!player.isOnline()) {
                    return;
                }

                sendCaptchaPrompt(player, code);
            }

        }.runTaskLater(plugin, 20L);
    }

    private String generateCaptchaCode() {

        int number =
                100000 + (int) (Math.random() * 900000);

        return String.valueOf(number);
    }

    /*
     * =========================
     * ÉTAPES DE VÉRIFICATION
     * =========================
     *
     * Chaque étape affiche un titre + une consigne dans le chat,
     * puis attend que le joueur tape sa réponse au chat.
     */

    private void sendCaptchaPrompt(Player player, String code) {

        player.sendTitle(
                "§cVérification anti-bot",
                "§eTapez le code dans le chat",
                10, 70, 20
        );

        player.sendMessage("§8§m--------------------------------");
        player.sendMessage("§c§lVÉRIFICATION ANTI-BOT");
        player.sendMessage("§7Tapez ce code dans le chat : §f§l" + code);
        player.sendMessage("§8§m--------------------------------");
    }

    private void sendServerNamePrompt(Player player) {

        player.sendTitle(
                "§bVérification",
                "§eNom du serveur ? (chat)",
                10, 70, 20
        );

        player.sendMessage("§8§m--------------------------------");
        player.sendMessage("§b§lQUEL EST LE NOM DE CE SERVEUR ?");
        player.sendMessage("§7Indice : c'est le nom affiché dans le launcher.");
        player.sendMessage("§7Répondez directement dans le chat.");
        player.sendMessage("§8§m--------------------------------");
    }

    private void sendPseudoPrompt(Player player) {

        player.sendTitle(
                "§dVérification",
                "§eVotre pseudo ? (chat)",
                10, 70, 20
        );

        player.sendMessage("§8§m--------------------------------");
        player.sendMessage("§d§lQUEL EST VOTRE PSEUDO ?");
        player.sendMessage("§7Répondez directement dans le chat.");
        player.sendMessage("§8§m--------------------------------");
    }

    /*
     * =========================
     * CAPTURE DU CHAT
     * =========================
     *
     * AsyncPlayerChatEvent est asynchrone : on ne touche pas à
     * l'API Bukkit directement dedans, tout le traitement est
     * renvoyé sur le thread principal via runTask.
     */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {

        Player player = event.getPlayer();

        if (!isVerifying(player)) {
            return;
        }

        /*
         * On bloque systématiquement le message :
         * il ne doit jamais être diffusé publiquement,
         * qu'il soit correct ou non.
         */
        event.setCancelled(true);

        String typed = event.getMessage().trim();

        new BukkitRunnable() {

            @Override
            public void run() {
                handleAnswer(player, typed);
            }

        }.runTask(plugin);
    }

    private void handleAnswer(Player player, String typed) {

        VerificationSession session =
                sessions.get(player.getUniqueId());

        if (session == null) {
            return;
        }

        switch (session.getStep()) {

            case CAPTCHA:
                handleCaptchaAnswer(player, session, typed);
                break;

            case SERVER_NAME:
                handleServerNameAnswer(player, session, typed);
                break;

            case PSEUDO:
                handlePseudoAnswer(player, session, typed);
                break;

            case DONE:
                break;
        }
    }

    /*
     * =========================
     * CAPTCHA
     * =========================
     */

    private void handleCaptchaAnswer(
            Player player,
            VerificationSession session,
            String typed
    ) {

        if (typed.equals(session.getCaptchaCode())) {

            session.setStep(VerificationSession.Step.SERVER_NAME);
            sendServerNamePrompt(player);

        } else {

            failAttempt(player, session, "§cCode incorrect, réessayez.");

            if (!isVerifying(player)) {
                return;
            }

            sendCaptchaPrompt(player, session.getCaptchaCode());
        }
    }

    /*
     * =========================
     * NOM DU SERVEUR
     * =========================
     */

    private void handleServerNameAnswer(
            Player player,
            VerificationSession session,
            String typed
    ) {

        if (typed.equalsIgnoreCase(plugin.getServerName())) {

            session.setStep(VerificationSession.Step.PSEUDO);
            sendPseudoPrompt(player);

        } else {

            failAttempt(player, session, "§cMauvaise réponse, réessayez.");

            if (!isVerifying(player)) {
                return;
            }

            sendServerNamePrompt(player);
        }
    }

    /*
     * =========================
     * PSEUDO
     * =========================
     */

    private void handlePseudoAnswer(
            Player player,
            VerificationSession session,
            String typed
    ) {

        if (typed.equalsIgnoreCase(player.getName())) {

            session.setStep(VerificationSession.Step.DONE);
            finishVerification(player);

        } else {

            failAttempt(player, session, "§cCe n'est pas votre pseudo, réessayez.");

            if (!isVerifying(player)) {
                return;
            }

            sendPseudoPrompt(player);
        }
    }

    /*
     * =========================
     * ÉCHEC
     * =========================
     */

    private void failAttempt(
            Player player,
            VerificationSession session,
            String message
    ) {

        session.incrementAttempts();

        player.sendMessage(message);

        if (session.getAttempts() >= plugin.getMaxAttempts()) {

            sessions.remove(player.getUniqueId());

            player.kickPlayer(
                    "§cTrop de tentatives échouées.\n"
                            + "§7Reconnectez-vous pour réessayer."
            );
        }
    }

    /*
     * =========================
     * VALIDATION
     * =========================
     */

    private void finishVerification(Player player) {

        String ip = getIp(player);

        /*
         * L'IP est maintenant considérée
         * comme validée.
         */
        ipStore.validate(ip);

        sessions.remove(player.getUniqueId());

        player.setGameMode(plugin.getDefaultGameMode());

        player.setInvulnerable(false);
        player.setAllowFlight(false);

        player.sendTitle(
                "§aVérifié !",
                "§7Bienvenue " + plugin.getServerName(),
                10, 40, 10
        );

        player.sendMessage("§aVérification réussie !");
        player.sendMessage("§7Bienvenue sur §f" + plugin.getServerName() + "§7.");
    }

    /*
     * =========================
     * DÉCONNEXION
     * =========================
     */

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    /*
     * =========================
     * MOUVEMENT
     * =========================
     */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {

        if (!isVerifying(event.getPlayer())) {
            return;
        }

        if (hasMoved(event)) {
            event.setTo(event.getFrom());
        }
    }

    private boolean hasMoved(PlayerMoveEvent event) {

        if (event.getTo() == null) {
            return false;
        }

        return event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ();
    }

    /*
     * =========================
     * COMMANDES
     * =========================
     *
     * Priorité LOWEST : la vérification s'exécute et bloque la
     * commande AVANT que AuthMe (ou tout autre plugin) ne la
     * traite, y compris /login et /register.
     */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {

        Player player = event.getPlayer();

        if (!isVerifying(player)) {
            return;
        }

        event.setCancelled(true);

        player.sendMessage("§cTerminez d'abord la vérification anti-bot.");
    }

    /*
     * =========================
     * DÉGÂTS
     * =========================
     */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (isVerifying(player)) {
            event.setCancelled(true);
        }
    }

    /*
     * =========================
     * FAIM
     * =========================
     */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodChange(FoodLevelChangeEvent event) {

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (isVerifying(player)) {
            event.setCancelled(true);
        }
    }

    /*
     * =========================
     * CASSE
     * =========================
     */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {

        if (isVerifying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /*
     * =========================
     * POSE
     * =========================
     */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {

        if (isVerifying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /*
     * =========================
     * INTERACTION
     * =========================
     */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {

        if (isVerifying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /*
     * =========================
     * DROP
     * =========================
     */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDropItem(PlayerDropItemEvent event) {

        if (isVerifying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}

package su.nightexpress.excellentjobs.job.listener;

import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentjobs.JobsPlugin;
import su.nightexpress.excellentjobs.config.Keys;
import su.nightexpress.excellentjobs.job.JobManager;
import su.nightexpress.nightcore.manager.AbstractListener;
import su.nightexpress.nightcore.util.PDCUtil;

public class JobGenericListener extends AbstractListener<JobsPlugin> {

    private final JobManager jobManager;

    public JobGenericListener(@NotNull JobsPlugin plugin, @NotNull JobManager jobManager) {
        super(plugin);
        this.jobManager = jobManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        this.jobManager.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        this.jobManager.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Firework firework)) return;
        if (PDCUtil.getBoolean(firework, Keys.levelFirework).orElse(false)) {
            event.setCancelled(true);
        }
    }
}

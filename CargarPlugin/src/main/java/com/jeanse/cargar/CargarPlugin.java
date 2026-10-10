package com.jeanse.cargar;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CargarPlugin extends JavaPlugin implements Listener {

    // Lo que lleva cada jugador: puede ser un mob/animal o un bloque (con su display visual)
    private final Map<UUID, Entity> entidades = new HashMap<>();
    private final Map<UUID, BlockData> bloques = new HashMap<>();
    private final Map<UUID, BlockDisplay> displays = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (UUID id : new HashMap<>(entidades).keySet()) {
            Player p = getServer().getPlayer(id);
            if (p != null) soltarTodo(p);
        }
    }

    // Mensaje en la barra de acción (compatible con Spigot)
    private void mensaje(Player p, String texto) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(texto));
    }

    private boolean llevaAlgo(Player p) {
        return entidades.containsKey(p.getUniqueId()) || bloques.containsKey(p.getUniqueId());
    }

    // ---------- Cargar animales / mobs ----------
    @EventHandler
    public void alInteractuarConEntidad(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        if (!p.getInventory().getItemInMainHand().getType().isAir()) return;
        if (llevaAlgo(p)) return;

        Entity objetivo = e.getRightClicked();
        if (objetivo instanceof Player || objetivo instanceof BlockDisplay) return;

        e.setCancelled(true);
        p.addPassenger(objetivo);
        entidades.put(p.getUniqueId(), objetivo);
        mensaje(p, "Cargando: " + objetivo.getType().name());
    }

    // ---------- Cargar / colocar bloques y soltar entidades ----------
    @EventHandler
    public void alInteractuar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        if (!p.getInventory().getItemInMainHand().getType().isAir()) return;

        Block clic = e.getClickedBlock();
        if (clic == null) return;
        e.setCancelled(true);

        Block destino = clic.getRelative(e.getBlockFace());

        // 1) Si lleva un bloque: colocarlo
        if (bloques.containsKey(p.getUniqueId())) {
            if (!destino.getType().isAir()) return;
            destino.setBlockData(bloques.get(p.getUniqueId()));
            quitarBloque(p);
            return;
        }

        // 2) Si lleva un animal/mob: soltarlo
        if (entidades.containsKey(p.getUniqueId())) {
            Entity ent = entidades.remove(p.getUniqueId());
            p.removePassenger(ent);
            ent.teleport(destino.getLocation().add(0.5, 0, 0.5));
            return;
        }

        // 3) Si no lleva nada: recoger el bloque
        Material tipo = clic.getType();
        if (tipo == Material.BEDROCK || tipo == Material.BARRIER
                || clic.getState() instanceof TileState) {
            mensaje(p, "No puedes cargar ese bloque");
            return;
        }

        BlockData datos = clic.getBlockData();
        clic.setType(Material.AIR);
        bloques.put(p.getUniqueId(), datos);

        // Bloque visible sobre la cabeza
        Location loc = p.getLocation();
        BlockDisplay display = p.getWorld().spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(datos);
            d.setTransformation(new Transformation(
                    new Vector3f(-0.25f, 0f, -0.25f),
                    new Quaternionf(),
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Quaternionf()));
        });
        p.addPassenger(display);
        displays.put(p.getUniqueId(), display);
    }

    // ---------- Seguridad ----------
    private void quitarBloque(Player p) {
        bloques.remove(p.getUniqueId());
        BlockDisplay d = displays.remove(p.getUniqueId());
        if (d != null) {
            p.removePassenger(d);
            d.remove();
        }
    }

    private void soltarTodo(Player p) {
        Entity ent = entidades.remove(p.getUniqueId());
        if (ent != null) p.removePassenger(ent);

        BlockData datos = bloques.get(p.getUniqueId());
        if (datos != null) {
            Block b = p.getLocation().getBlock();
            if (b.getType().isAir()) b.setBlockData(datos);
            else p.getWorld().dropItemNaturally(p.getLocation(), new org.bukkit.inventory.ItemStack(datos.getMaterial()));
            quitarBloque(p);
        }
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) { soltarTodo(e.getPlayer()); }

    @EventHandler
    public void alMorir(PlayerDeathEvent e) { soltarTodo(e.getEntity()); }
}

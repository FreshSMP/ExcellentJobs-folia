package su.nightexpress.excellentjobs.zone;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellentjobs.JobsPlugin;
import su.nightexpress.excellentjobs.config.Config;
import su.nightexpress.excellentjobs.config.Keys;
import su.nightexpress.excellentjobs.config.Lang;
import su.nightexpress.excellentjobs.hook.HookPlugin;
import su.nightexpress.excellentjobs.util.Modifier;
import su.nightexpress.excellentjobs.zone.command.ZoneCommands;
import su.nightexpress.excellentjobs.zone.editor.*;
import su.nightexpress.excellentjobs.zone.impl.BlockList;
import su.nightexpress.excellentjobs.zone.impl.Selection;
import su.nightexpress.excellentjobs.zone.impl.Zone;
import su.nightexpress.excellentjobs.zone.listener.GenericZoneListener;
import su.nightexpress.excellentjobs.zone.listener.SelectionZoneListener;
import su.nightexpress.excellentjobs.zone.visual.BlockHighlighter;
import su.nightexpress.excellentjobs.zone.visual.BlockInfo;
import su.nightexpress.excellentjobs.zone.visual.BlockPacketsHighlighter;
import su.nightexpress.excellentjobs.zone.visual.BlockProtocolHighlighter;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.*;
import su.nightexpress.nightcore.util.geodata.Cuboid;
import su.nightexpress.nightcore.util.geodata.pos.BlockPos;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class ZoneManager extends AbstractManager<JobsPlugin> {

    private final Map<String, Zone>    zoneMap;
    private final Map<UUID, Selection> selectionMap;

    private ZoneListEditor     zoneListEditor;
    private ZoneEditor         zoneEditor;
    private ZoneHoursEditor    zoneHoursEditor;
    private ModifierListEditor modifierListEditor;
    private ModifierEditor     modifierEditor;
    private BlocksEditor       blocksEditor;
    private BlockListEditor    blockListEditor;

    private BlockHighlighter highlighter;

    public ZoneManager(@NotNull JobsPlugin plugin) {
        super(plugin);
        this.zoneMap = new HashMap<>();
        this.selectionMap = new HashMap<>();
    }

    @Override
    protected void onLoad() {
        this.loadCommands();
        this.loadHighlighter();
        this.loadZones();
        this.loadEditor();

        this.addListener(new GenericZoneListener(this.plugin, this));
        this.addListener(new SelectionZoneListener(this.plugin, this));

        this.addTask(this::regenerateBlocks, Config.ZONES_REGENERATION_TASK_INTERVAL.get());
    }

    @Override
    protected void onShutdown() {
        this.regenerateBlocks(true);

        this.zoneListEditor.clear();
        this.zoneEditor.clear();
        this.zoneHoursEditor.clear();
        this.modifierListEditor.clear();
        this.blocksEditor.clear();
        this.blockListEditor.clear();
        this.modifierEditor.clear();

        this.zoneMap.clear();

        if (this.highlighter != null) this.highlighter = null;

        ZoneCommands.unload(this.plugin);
    }

    private void loadCommands() {
        ZoneCommands.load(this.plugin, this);
    }

    private void loadHighlighter() {
        if (Version.isBehind(Version.V1_20_R3)) return;

        if (Plugins.isInstalled(HookPlugin.PACKET_EVENTS)) {
            this.highlighter = new BlockPacketsHighlighter(this.plugin);
        }
        else if (Plugins.isInstalled(HookPlugin.PROTOCOL_LIB)) {
            this.highlighter = new BlockProtocolHighlighter(this.plugin);
        }
    }

    private void loadEditor() {
        this.zoneListEditor = new ZoneListEditor(this.plugin);
        this.zoneEditor = new ZoneEditor(this.plugin, this);
        this.zoneHoursEditor = new ZoneHoursEditor(this.plugin, this);
        this.modifierListEditor = new ModifierListEditor(this.plugin, this);
        this.blocksEditor = new BlocksEditor(this.plugin, this);
        this.blockListEditor = new BlockListEditor(this.plugin, this);
        this.modifierEditor = new ModifierEditor(this.plugin, this);
    }

    private void loadZones() {
        for (File file : FileUtil.getFiles(this.getZonesDirectory())) {
            Zone zone = new Zone(plugin, file);
            this.loadZone(zone);
        }
        this.plugin.info("Loaded " + this.zoneMap.size() + " job zones.");
    }

    private boolean loadZone(@NotNull Zone zone) {
        if (zone.load()) {
            zone.reactivate();
            this.zoneMap.put(zone.getId(), zone);
            return true;
        }
        else this.plugin.error("Zone not loaded: '" + zone.getFile().getName() + "'.");
        return false;
    }

    public boolean defineZone(@NotNull Player player, @NotNull String name) {
        String id = StringUtil.lowerCaseUnderscoreStrict(name);

        if (this.getZoneById(id) != null) {
            Lang.ZONE_ERROR_EXISTS.message().send(player, replacer -> replacer.replace(Placeholders.GENERIC_NAME, id));
            return false;
        }

        Selection selection = this.getSelection(player);
        if (selection == null) {
            Lang.ZONE_ERROR_NO_SELECTION.message().send(player);
            return false;
        }

        Cuboid cuboid = selection.toCuboid();
        if (cuboid == null) {
            Lang.ZONE_ERROR_INCOMPLETE_SELECTION.message().send(player);
            return false;
        }

        World world = player.getWorld();
        Zone zone = this.createZone(world, cuboid, id);

        this.exitSelection(player);
        this.openEditor(player, zone);

        Lang.ZONE_CREATE_SUCCESS.message().send(player, replacer -> replacer.replace(zone.replacePlaceholders()));
        return true;
    }

    @NotNull
    public Zone createZone(@NotNull World world, @NotNull Cuboid cuboid, @NotNull String id) {
        Zone current = this.getZoneById(id);
        if (current != null) return current;

        File file = new File(this.getZonesDirectory(), id + ".yml");
        Zone zone = new Zone(plugin, file);
        zone.setWorldName(world.getName());
        zone.setCuboid(cuboid);
        zone.setName(StringUtil.capitalizeUnderscored(id));
        zone.setMinJobLevel(-1);
        zone.setMaxJobLevel(-1);
        zone.save();

        this.loadZone(zone);
        return zone;
    }

    public boolean deleteZone(@NotNull String id) {
        Zone zone = this.zoneMap.remove(id.toLowerCase());
        return zone != null && zone.getFile().delete();
    }

    @NotNull
    public String getZonesDirectory() {
        return this.plugin.getDataFolder() + Config.DIR_ZONES;
    }

    @NotNull
    public Map<String, Zone> getZoneMap() {
        return zoneMap;
    }

    @NotNull
    public Set<Zone> getZones() {
        return new HashSet<>(this.zoneMap.values());
    }

    @NotNull
    public Set<Zone> getActiveZones() {
        return this.getZones().stream().filter(Zone::isActive).collect(Collectors.toSet());
    }

    @NotNull
    public List<String> getZoneIds() {
        return new ArrayList<>(this.zoneMap.keySet());
    }

    @NotNull
    public Set<Zone> getZones(@NotNull World world) {
        return this.getZones(world.getName());
    }

    @NotNull
    public Set<Zone> getZones(@NotNull String worldName) {
        return this.getZones().stream().filter(zone -> zone.getWorldName().equalsIgnoreCase(worldName)).collect(Collectors.toSet());
    }

    @Nullable
    public Zone getZoneById(@NotNull String id) {
        return this.zoneMap.get(id.toLowerCase());
    }

    @Nullable
    public Zone getZoneByLocation(@NotNull Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        return this.getZones(world).stream().filter(zone -> zone.contains(location)).findFirst().orElse(null);
    }

    @Nullable
    public Zone getZoneByWandItem(@NotNull ItemStack item) {
        String id = PDCUtil.getString(item, Keys.wandZoneId).orElse(null);
        return id == null ? null : this.getZoneById(id);
    }

    @Nullable
    public Zone getZone(@NotNull Entity entity) {
        return this.getZoneByLocation(entity.getLocation());
    }

    @Nullable
    public Zone getZone(@NotNull Block block) {
        return this.getZoneByLocation(block.getLocation());
    }

    public boolean isInZone(@NotNull Block block) {
        return this.getZone(block) != null;
    }

    public boolean isInZone(@NotNull Entity entity) {
        return this.getZone(entity) != null;
    }

    public void openEditor(@NotNull Player player) {
        this.zoneListEditor.open(player, this);
    }

    public void openEditor(@NotNull Player player, @NotNull Zone zone) {
        this.zoneEditor.open(player, zone);
    }

    public void openTimesEditor(@NotNull Player player, @NotNull Zone zone) {
        this.zoneHoursEditor.open(player, zone);
    }

    public void openModifiersEditor(@NotNull Player player, @NotNull Zone zone) {
        this.modifierListEditor.open(player, zone);
    }

    public void openModifierEditor(@NotNull Player player, @NotNull Zone zone, @NotNull Modifier modifier) {
        this.modifierEditor.open(player, zone, modifier);
    }

    public void openBlocksEditor(@NotNull Player player, @NotNull Zone zone) {
        this.blocksEditor.open(player, zone);
    }

    public void openBlockListEditor(@NotNull Player player, @NotNull Zone zone, @NotNull BlockList blockList) {
        this.blockListEditor.open(player, zone, blockList);
    }

    public void regenerateBlocks() {
        this.regenerateBlocks(false);
    }

    public void regenerateBlocks(boolean force) {
        this.getActiveZones().forEach(zone -> zone.regenerateBlocks(force));
    }

    @NotNull
    public ItemStack getCuboidWand(@Nullable Zone zone) {
        ItemStack item = Config.ZONES_WAND_ITEM.get().getItemStack();
        if (zone != null) {
            PDCUtil.set(item, Keys.wandZoneId, zone.getId());
        }
        PDCUtil.set(item, Keys.wandItem, true);
        return item;
    }

    public boolean isCuboidWand(@NotNull ItemStack itemStack) {
        return PDCUtil.getBoolean(itemStack, Keys.wandItem).isPresent();
    }

    public boolean isInSelection(@NotNull Player player) {
        return this.getSelection(player) != null;
    }

    @Nullable
    public Selection getSelection(@NotNull Player player) {
        return this.selectionMap.get(player.getUniqueId());
    }

    @NotNull
    public Selection startSelection(@NotNull Player player, @Nullable Zone zone) {
        Selection selection = new Selection();
        this.selectionMap.put(player.getUniqueId(), selection);

        Players.addItem(player, this.getCuboidWand(zone));

        if (zone != null) {
            this.highlightCuboid(player, zone.getCuboid());
        }
        if (zone == null) {
            Lang.ZONE_CREATE_INFO.message().send(player);
        }

        return selection;
    }

    public void exitSelection(@NotNull Player player) {
        if (this.isInSelection(player)) {
            Players.takeItem(player, this::isCuboidWand);
            this.selectionMap.remove(player.getUniqueId());
        }
        this.removeVisuals(player);
    }

    public void selectPosition(@NotNull Player player, @NotNull ItemStack itemStack, @NotNull Location location, @NotNull Action action) {
        if (!this.isCuboidWand(itemStack)) return;

        Selection selection = this.getSelection(player);
        if (selection == null) return;

        BlockPos blockPos = BlockPos.from(location);
        if (action == Action.LEFT_CLICK_BLOCK) {
            selection.setFirst(blockPos);
        }
        else selection.setSecond(blockPos);

        int position = action == Action.LEFT_CLICK_BLOCK ? 1 : 2;
        Lang.ZONE_SELECTION_INFO.message().send(player, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, position));

        Cuboid cuboid = selection.toCuboid();
        Zone zone = this.getZoneByWandItem(itemStack);
        if (cuboid != null && zone != null) {
            World world = player.getWorld();

            zone.deactivate();
            zone.setWorldName(world.getName());
            zone.activate(world);
            zone.setCuboid(cuboid);
            zone.save();

            this.exitSelection(player);
            this.openEditor(player, zone);
        }
        else {
            this.highlightCuboid(player, selection);
        }
    }

    public void removeVisuals(@NotNull Player player) {
        if (this.highlighter != null) {
            this.highlighter.removeVisuals(player);
        }
    }

    public void highlightCuboid(@NotNull Player player, @NotNull Selection selection) {
        this.highlightCuboid(player, selection.getFirst(), selection.getSecond());
    }

    private void highlightCuboid(@NotNull Player player, @Nullable BlockPos min, @Nullable BlockPos max) {
        if (min == null) min = BlockPos.empty();
        if (max == null) max = BlockPos.empty();
        if (min.isEmpty() && !max.isEmpty()) min = max;
        if (max.isEmpty() && !min.isEmpty()) max = min;

        this.highlightCuboid(player, new Cuboid(min, max));
    }

    public void highlightCuboid(@NotNull Player player, @NotNull Cuboid cuboid) {
        this.highlightCuboid(player, cuboid, true);
    }

    public void highlightCuboid(@NotNull Player player, @NotNull Cuboid cuboid, boolean reset) {
        if (this.highlighter == null) return;

        if (reset) {
            this.removeVisuals(player);
        }

        World world = player.getWorld();
        Material cornerType = Config.getHighlightCorner();
        Material wireType = Config.getHighlightWire();
        Set<BlockInfo> dataSet = new HashSet<>();

        // Draw corners of the chunk/region all the time.
        this.collectBlockData(cuboid.getCorners(), dataSet, cornerType.createBlockData());
        this.collectBlockData(cuboid.getCornerWiresY(), dataSet, wireType.createBlockData());

        // Draw connections only for regions or when player is inside a chunk.
        BlockData dataX = this.createBlockData(wireType, Axis.X);
        BlockData dataZ = this.createBlockData(wireType, Axis.Z);

        this.collectBlockData(cuboid.getCornerWiresX(), dataSet, dataX);
        this.collectBlockData(cuboid.getCornerWiresZ(), dataSet, dataZ);

        // Draw all visual blocks at prepated positions with prepared block data.
        dataSet.forEach(blockInfo -> {
            BlockPos blockPos = blockInfo.getBlockPos();
            Location location = blockPos.toLocation(world);

            this.highlighter.addVisualBlock(player, location, blockInfo.getBlockData());
        });
    }

    private void collectBlockData(@NotNull Collection<BlockPos> source, @NotNull Set<BlockInfo> target, @NotNull BlockData data) {
        if (data.getMaterial().isAir()) return;

        source.stream().filter(blockPos -> blockPos != null && !blockPos.isEmpty()).map(blockPos -> new BlockInfo(blockPos, data)).forEach(target::add);
    }

    @NotNull
    private BlockData createBlockData(@NotNull Material material, @NotNull Axis axis) {
        BlockData data = material.createBlockData();
        if (data instanceof Orientable orientable) {
            orientable.setAxis(axis);
        }
        return data;
    }
}
